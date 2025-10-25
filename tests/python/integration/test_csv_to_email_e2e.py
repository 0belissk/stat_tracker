import csv
import io
import json
import os
import shutil
import subprocess
import time
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Dict, Iterable, Tuple

import pytest

boto3 = pytest.importorskip("boto3")
from botocore.config import Config
from testcontainers.localstack import LocalStackContainer

from lambdas.stats_quality_check.handler import handler as quality_handler, reset_caches

ROOT_DIR = Path(__file__).resolve().parents[3]
NODE_RUNNER = ROOT_DIR / "tests" / "node" / "run_handler.js"

CSV_VALIDATE_DIST = ROOT_DIR / "lambdas" / "csv-validate" / "dist" / "handler.js"
PERSIST_BATCH_DIST = ROOT_DIR / "lambdas" / "persist-batch" / "dist" / "handler.js"
NOTIFY_READY_DIST = ROOT_DIR / "lambdas" / "notify-report-ready" / "dist" / "handler.js"


@contextmanager
def applied_env(overrides: Dict[str, str]) -> Iterable[None]:
    previous: Dict[str, str | None] = {}
    try:
        for key, value in overrides.items():
            previous[key] = os.environ.get(key)
            os.environ[key] = value
        yield
    finally:
        for key, old_value in previous.items():
            if old_value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = old_value


def _ensure_built(path: Path) -> None:
    if not path.exists():
        raise RuntimeError(
            f"Lambda bundle {path} does not exist. Ensure npm build completed before running tests."
        )


def _build_lambdas() -> None:
    if not shutil.which("npm"):
        raise RuntimeError("npm is required to build TypeScript lambdas for integration tests")

    for directory in ["lambdas/csv-validate", "lambdas/persist-batch", "lambdas/notify-report-ready"]:
        subprocess.run(["npm", "run", "build"], cwd=ROOT_DIR / directory, check=True)

    for dist_path in [CSV_VALIDATE_DIST, PERSIST_BATCH_DIST, NOTIFY_READY_DIST]:
        _ensure_built(dist_path)


@pytest.fixture(scope="session", autouse=True)
def _build_artifacts() -> None:
    _build_lambdas()


@pytest.fixture(scope="module")
def localstack_container():
    if not shutil.which("docker") or not os.path.exists("/var/run/docker.sock"):
        pytest.skip("Docker daemon is required for LocalStack integration tests", allow_module_level=True)

    container = LocalStackContainer(image="localstack/localstack:1.5.0").with_services(
        "s3", "dynamodb", "secretsmanager", "sesv2", "events"
    )
    with container as started:
        yield started


@pytest.fixture(scope="module")
def aws_clients(localstack_container) -> Dict[str, Any]:
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
        "secretsmanager": session.client("secretsmanager", endpoint_url=endpoint, config=config),
        "sesv2": session.client("sesv2", endpoint_url=endpoint, config=config),
    }


def _invoke_node_handler(dist_path: Path, event: Dict[str, Any], env: Dict[str, str]) -> Any:
    completed = subprocess.run(
        ["node", str(NODE_RUNNER), str(dist_path)],
        input=json.dumps(event),
        text=True,
        capture_output=True,
        check=True,
        env=env,
    )
    stdout = completed.stdout.strip()
    if stdout:
        return json.loads(stdout)
    return None


def _wait_for_table(dynamodb, table_name: str, timeout: float = 15.0) -> None:
    start = time.time()
    while time.time() - start < timeout:
        status = dynamodb.describe_table(TableName=table_name)["Table"]["TableStatus"]
        if status == "ACTIVE":
            return
        time.sleep(0.5)
    raise RuntimeError(f"Table {table_name} did not become ACTIVE within {timeout} seconds")


def _bootstrap_reports_table(dynamodb, table_name: str) -> None:
    dynamodb.create_table(
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
    _wait_for_table(dynamodb, table_name)


def _transform_csv_rows(csv_text: str, ingestion_id: str) -> Tuple[Dict[str, Any], ...]:
    reader = csv.DictReader(io.StringIO(csv_text))
    reports = []
    for index, row in enumerate(reader):
        base_columns = {"playerId", "playerEmail", "playerName", "coachId", "reportTimestamp"}
        categories = {
            column: value.strip()
            for column, value in row.items()
            if column not in base_columns and value is not None and value.strip()
        }
        report_id = f"{ingestion_id}-{row['playerId']}-{index + 1:03d}"
        reports.append(
            {
                "reportId": report_id,
                "playerId": row["playerId"].strip(),
                "coachId": row["coachId"].strip(),
                "reportTimestamp": row["reportTimestamp"].strip(),
                "categories": categories,
                "playerEmail": row["playerEmail"].strip(),
                "playerName": row["playerName"].strip(),
            }
        )
    return tuple(reports)

def _base_env(endpoint: str, region: str, extra: Dict[str, str] | None = None) -> Dict[str, str]:
    env = os.environ.copy()
    env.update(
        {
            "AWS_ACCESS_KEY_ID": "test",
            "AWS_SECRET_ACCESS_KEY": "test",
            "AWS_SESSION_TOKEN": "test",
            "AWS_REGION": region,
            "AWS_DEFAULT_REGION": region,
            "AWS_ENDPOINT_URL": endpoint,
        }
    )
    if extra:
        env.update(extra)
    return env


def test_csv_upload_to_email_delivery(aws_clients):
    endpoint = aws_clients["endpoint"]
    region = aws_clients["region"]
    s3 = aws_clients["s3"]
    dynamodb = aws_clients["dynamodb"]
    events = aws_clients["events"]
    secrets = aws_clients["secretsmanager"]
    ses = aws_clients["sesv2"]

    ingestion_id = "ing-20250315"
    raw_bucket = "vsm-raw-uploads"
    error_prefix = "errors/"
    csv_key = "uploads/2025/ingestion.csv"
    csv_body = (
        "playerId,playerEmail,playerName,coachId,reportTimestamp,serving,passing\n"
        "player-1,alex.li@example.edu,Alex Li,coach-5,2024-02-04T00:00:00Z,Great serves,Clean passing\n"
        "player-2,marco.lee@example.edu,Marco Lee,coach-5,2024-02-04T00:00:00Z,Powerful jump serve,Quick reactions\n"
    )

    s3.create_bucket(Bucket=raw_bucket)
    s3.put_object(Bucket=raw_bucket, Key=csv_key, Body=csv_body)

    csv_event = {
        "Records": [
            {
                "s3": {
                    "bucket": {"name": raw_bucket},
                    "object": {"key": csv_key.replace('/', '%2F')},
                }
            }
        ]
    }

    timings: Dict[str, float] = {}
    overall_start = time.perf_counter()

    stage_start = time.perf_counter()
    csv_env = _base_env(
        endpoint,
        region,
        {
            "S3_ENDPOINT_URL": endpoint,
            "ERROR_REPORT_BUCKET": raw_bucket,
            "ERROR_REPORT_PREFIX": error_prefix,
        },
    )
    _invoke_node_handler(CSV_VALIDATE_DIST, csv_event, csv_env)
    timings["csv_validation"] = time.perf_counter() - stage_start

    error_objects = s3.list_objects_v2(Bucket=raw_bucket, Prefix=error_prefix)
    assert error_objects.get("KeyCount", 0) == 0

    reports = _transform_csv_rows(csv_body, ingestion_id)

    quality_rules_bucket = "quality-rules"
    quality_rules_key = "rules.json"
    events_bus = "stat-quality"
    reports_table = "reports-table"

    s3.create_bucket(Bucket=quality_rules_bucket)
    s3.put_object(
        Bucket=quality_rules_bucket,
        Key=quality_rules_key,
        Body=json.dumps(
            {
                "requiredCategories": ["serving", "passing"],
                "allowedCategories": ["serving", "passing"],
                "maxCategoryLength": 500,
                "maxCategoriesPerReport": 5,
            }
        ),
    )
    events.create_event_bus(Name=events_bus)

    dynamodb.create_table(
        TableName=reports_table,
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
    _wait_for_table(dynamodb, reports_table)

    quality_env = {
        "AWS_ACCESS_KEY_ID": "test",
        "AWS_SECRET_ACCESS_KEY": "test",
        "AWS_SESSION_TOKEN": "test",
        "AWS_REGION": region,
        "AWS_DEFAULT_REGION": region,
        "AWS_ENDPOINT_URL": endpoint,
        "QUALITY_RULES_BUCKET": quality_rules_bucket,
        "QUALITY_RULES_KEY": quality_rules_key,
        "QUALITY_EVENT_BUS_NAME": events_bus,
        "QUALITY_EVENT_SOURCE": "stat.tracker.tests",
        "QUALITY_EVENT_DETAIL_TYPE": "csv.quality",
        "REPORTS_TABLE_NAME": reports_table,
    }

    correlation_id = "corr-" + ingestion_id
    ingest_started_at = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())

    stage_start = time.perf_counter()
    reset_caches()
    with applied_env(quality_env):
        quality_result = quality_handler(
            {
                "ingestionId": ingestion_id,
                "sourceBucket": raw_bucket,
                "sourceKey": csv_key,
                "ingestStartedAt": ingest_started_at,
                "correlationId": correlation_id,
                "traceHeader": "Root=1-abcdef01-abcdef0123456789;Parent=abcdef0123456789",
                "reports": list(reports),
            },
            None,
        )
    timings["quality_check"] = time.perf_counter() - stage_start

    stage_start = time.perf_counter()
    persist_env = _base_env(
        endpoint,
        region,
        {
            "DYNAMODB_ENDPOINT_URL": endpoint,
            "REPORTS_TABLE_NAME": reports_table,
        },
    )
    persist_result = _invoke_node_handler(PERSIST_BATCH_DIST, quality_result, persist_env)
    timings["persistence"] = time.perf_counter() - stage_start

    assert persist_result["tableName"] == reports_table
    assert persist_result["total"] == len(reports)
    assert persist_result["processed"] == len(reports)
    assert persist_result["skipped"] == 0
    assert persist_result["correlationId"] == correlation_id
    assert isinstance(persist_result["ingestDurationMs"], (int, float))
    assert persist_result["ingestDurationMs"] >= 0

    report_bucket = "player-report-texts"
    audit_bucket = "email-delivery-audit"
    s3.create_bucket(Bucket=report_bucket)
    s3.create_bucket(Bucket=audit_bucket)

    delivered_reports = []
    for report in reports:
        report_key = f"reports/{report['reportId']}.txt"
        body = f"Report for {report['playerName']}\nserving: {report['categories']['serving']}\n"
        s3.put_object(Bucket=report_bucket, Key=report_key, Body=body)
        report_name = f"Weekly training summary #{report['reportId'][-3:]}"
        delivered_reports.append((report, report_key, report_name))

    secret_name = "stat-tracker/notify-report-ready"
    sender_email = "reports@example.com"

    ses.create_email_identity(EmailIdentity=sender_email)

    secret_payload = json.dumps(
        {
            "senderEmail": sender_email,
            "emailSubject": "Report ready: {{reportName}}",
            "emailTemplate": (
                "Hello {{recipientName}}, your {{reportType}} report ({{reportName}}) is ready. "
                "Download it here: {{downloadUrl}}"
            ),
            "linkExpirySeconds": 3600,
        }
    )
    secret_result = secrets.create_secret(Name=secret_name, SecretString=secret_payload)

    stage_start = time.perf_counter()
    for report, report_key, report_name in delivered_reports:
        notify_env = _base_env(
            endpoint,
            region,
            {
                "SECRETSMANAGER_ENDPOINT_URL": endpoint,
                "SES_ENDPOINT_URL": endpoint,
                "S3_ENDPOINT_URL": endpoint,
                "CONFIG_SECRET_ARN": secret_result["ARN"],
                "EMAIL_AUDIT_BUCKET": audit_bucket,
                "EMAIL_AUDIT_PREFIX": "email-outbox/",
            },
        )
        notify_event = {
            "id": "1",
            "version": "0",
            "time": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "region": region,
            "resources": [],
            "source": "stat.tracker.reports",
            "detail-type": "report.created",
            "detail": {
                "reportId": report["reportId"],
                "reportName": report_name,
                "reportType": "player-report",
                "recipientEmail": report["playerEmail"],
                "recipientName": report["playerName"],
                "s3Bucket": report_bucket,
                "s3Key": report_key,
                "ingestStartedAt": ingest_started_at,
                "correlationId": correlation_id,
                "traceHeader": "Root=1-abcdef01-abcdef0123456789;Parent=abcdef0123456789",
            },
        }
        _invoke_node_handler(NOTIFY_READY_DIST, notify_event, notify_env)
    timings["notification"] = time.perf_counter() - stage_start

    audit_objects = s3.list_objects_v2(Bucket=audit_bucket).get("Contents", [])
    assert audit_objects, "Expected SES audit artifacts to be created"

    audit_payloads: Dict[str, Dict[str, Any]] = {}
    for obj in audit_objects:
        key = obj["Key"]
        body = s3.get_object(Bucket=audit_bucket, Key=key)["Body"].read().decode("utf-8")
        payload = json.loads(body)
        report_id = payload.get("reportId")
        assert report_id, f"Audit payload missing reportId for object {key}"
        audit_payloads[report_id] = payload

    for report, _, report_name in delivered_reports:
        payload = audit_payloads.get(report["reportId"])
        assert payload is not None, f"Missing audit payload for {report['reportId']}"
        assert payload["recipientEmail"] == report["playerEmail"]
        assert report["playerName"] in payload["body"]
        assert report_name in payload["subject"]
        assert report["reportId"] in payload["downloadUrl"]

    overall_duration = time.perf_counter() - overall_start
    assert overall_duration < 60, f"Pipeline exceeded SLO: {overall_duration:.2f}s"

    print(json.dumps({"pipelineTimings": timings, "totalSeconds": overall_duration}, indent=2))
