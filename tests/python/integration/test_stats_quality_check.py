import json
import os
import shutil
import sys
import time
from pathlib import Path
from typing import Any, Dict, Iterable

import boto3
import pytest
from botocore.config import Config
from testcontainers.localstack import LocalStackContainer

sys.path.append(str(Path(__file__).resolve().parents[3]))

from lambdas.stats_quality_check.handler import QualityCheckError, handler, reset_caches

@pytest.fixture(scope="module")
def localstack_container():
    if not shutil.which("docker") or not os.path.exists("/var/run/docker.sock"):
        pytest.skip("Docker daemon is required for LocalStack integration tests", allow_module_level=True)
    container = LocalStackContainer(image="localstack/localstack:1.5.0").with_services(
        "s3", "dynamodb", "events"
    )
    with container as started:
        yield started


@pytest.fixture(scope="module")
def aws_clients(localstack_container):
    endpoint = localstack_container.get_url()
    region = "us-east-1"

    session = boto3.session.Session(
        aws_access_key_id="test",
        aws_secret_access_key="test",
        aws_session_token="test",
        region_name=region,
    )

    config = Config(retries={"max_attempts": 10, "mode": "standard"})

    return {
        "endpoint": endpoint,
        "region": region,
        "s3": session.client("s3", endpoint_url=endpoint, config=config),
        "dynamodb": session.client("dynamodb", endpoint_url=endpoint, config=config),
        "events": session.client("events", endpoint_url=endpoint, config=config),
    }


@pytest.fixture(autouse=True)
def _clear_caches():
    reset_caches()
    yield
    reset_caches()


def _wait_for_table(dynamodb, table_name: str, timeout: float = 10.0) -> None:
    start = time.time()
    while time.time() - start < timeout:
        status = dynamodb.describe_table(TableName=table_name)["Table"]["TableStatus"]
        if status == "ACTIVE":
            return
        time.sleep(0.5)
    raise RuntimeError(f"Table {table_name} did not become ACTIVE within {timeout} seconds")


def _bootstrap_environment(clients: Dict[str, Any]) -> Dict[str, str]:
    bucket_name = "quality-rules"
    table_name = "reports-table"
    bus_name = "stat-quality"

    clients["s3"].create_bucket(Bucket=bucket_name)

    config_document = {
        "requiredCategories": ["serving", "passing"],
        "allowedCategories": ["serving", "passing", "defense"],
        "maxCategoryLength": 250,
        "maxCategoriesPerReport": 10,
    }
    clients["s3"].put_object(
        Bucket=bucket_name,
        Key="rules.json",
        Body=json.dumps(config_document).encode("utf-8"),
    )

    clients["events"].create_event_bus(Name=bus_name)

    clients["dynamodb"].create_table(
        TableName=table_name,
        AttributeDefinitions=[
            {"AttributeName": "PK", "AttributeType": "S"},
            {"AttributeName": "SK", "AttributeType": "S"},
            {"AttributeName": "GSI1PK", "AttributeType": "S"},
            {"AttributeName": "GSI1SK", "AttributeType": "S"},
        ],
        KeySchema=[
            {"AttributeName": "PK", "KeyType": "HASH"},
            {"AttributeName": "SK", "KeyType": "RANGE"},
        ],
        GlobalSecondaryIndexes=[
            {
                "IndexName": "GSI1",
                "KeySchema": [
                    {"AttributeName": "GSI1PK", "KeyType": "HASH"},
                    {"AttributeName": "GSI1SK", "KeyType": "RANGE"},
                ],
                "Projection": {"ProjectionType": "ALL"},
            }
        ],
        BillingMode="PAY_PER_REQUEST",
    )

    _wait_for_table(clients["dynamodb"], table_name)

    env = {
        "AWS_ACCESS_KEY_ID": "test",
        "AWS_SECRET_ACCESS_KEY": "test",
        "AWS_SESSION_TOKEN": "test",
        "AWS_REGION": clients["region"],
        "AWS_ENDPOINT_URL": clients["endpoint"],
        "QUALITY_RULES_BUCKET": bucket_name,
        "QUALITY_RULES_KEY": "rules.json",
        "QUALITY_EVENT_BUS_NAME": bus_name,
        "QUALITY_EVENT_SOURCE": "stat.tracker.tests",
        "QUALITY_EVENT_DETAIL_TYPE": "csv.quality",
        "REPORTS_TABLE_NAME": table_name,
    }
    return env


def _apply_env(overrides: Dict[str, str]) -> None:
    for key, value in overrides.items():
        os.environ[key] = value


def _reports_payload(report_overrides: Iterable[Dict[str, Any]] = ()):  # type: ignore[type-arg]
    base_report = {
        "reportId": "report-1",
        "playerId": "player-1",
        "coachId": "coach-1",
        "reportTimestamp": "2024-02-04T00:00:00Z",
        "categories": {"serving": "Excellent", "passing": "Great"},
    }

    reports = [dict(base_report)]
    for override in report_overrides:
        candidate = dict(base_report)
        candidate.update(override)
        reports.append(candidate)

    return {
        "ingestionId": "ingestion-1",
        "sourceBucket": "raw-bucket",
        "sourceKey": "reports/file.csv",
        "reports": reports,
    }


def test_quality_check_passes_and_emits_event(aws_clients):
    env = _bootstrap_environment(aws_clients)
    _apply_env(env)

    result = handler(_reports_payload(), None)

    assert result["qualityCheck"]["status"] == "passed"
    assert result["qualityCheck"]["eventBridgeEventId"]
    assert result["reports"][0]["categories"]["serving"] == "Excellent"


def test_quality_check_detects_duplicates_and_missing_categories(aws_clients):
    env = _bootstrap_environment(aws_clients)
    _apply_env(env)

    dynamodb = aws_clients["dynamodb"]
    dynamodb.put_item(
        TableName=env["REPORTS_TABLE_NAME"],
        Item={
            "PK": {"S": "PLAYER#player-1"},
            "SK": {"S": "REPORT#20240204T000000#report-1"},
            "GSI1PK": {"S": "REPORT#report-1"},
            "GSI1SK": {"S": "REPORT#report-1"},
        },
    )

    payload = _reports_payload(
        [
            {
                "reportId": "report-1",
                "categories": {"serving": "", "defense": "Ok"},
            }
        ]
    )

    with pytest.raises(QualityCheckError) as exc:
        handler(payload, None)

    error = exc.value
    assert error.summary["status"] == "failed"
    assert error.summary["failedReports"] == 2
    assert any(
        "Missing required categories" in issue
        for failure in error.failures
        for issue in failure["issues"]
    )
    assert any(
        "Report already exists" in issue
        for failure in error.failures
        for issue in failure["issues"]
    )
