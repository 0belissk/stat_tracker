import json
import logging
import os
from dataclasses import dataclass
from typing import Any, Dict, Iterable, List, Optional, Tuple

import boto3
from botocore.exceptions import ClientError

LOGGER = logging.getLogger(__name__)
LOGGER.setLevel(logging.INFO)

_CLIENT_CACHE: Dict[str, Any] = {}
_CONFIG_CACHE: Optional[Dict[str, Any]] = None


class QualityCheckError(Exception):
    """Raised when the quality check detects blocking issues."""

    def __init__(self, message: str, failures: List[Dict[str, Any]], summary: Dict[str, Any]):
        super().__init__(message)
        self.failures = failures
        self.summary = summary


@dataclass(frozen=True)
class QualityConfig:
    required_categories: Tuple[str, ...]
    allowed_categories: Optional[Tuple[str, ...]]
    max_category_length: Optional[int]
    max_categories_per_report: Optional[int]

    @staticmethod
    def from_dict(config: Dict[str, Any]) -> "QualityConfig":
        required_raw = config.get("requiredCategories") or []
        allowed_raw = config.get("allowedCategories")
        max_length = config.get("maxCategoryLength")
        max_count = config.get("maxCategoriesPerReport")

        required = tuple(
            sorted({str(cat).strip() for cat in required_raw if isinstance(cat, str) and cat.strip()})
        )
        allowed = (
            tuple(sorted({str(cat).strip() for cat in allowed_raw if isinstance(cat, str) and cat.strip()}))
            if isinstance(allowed_raw, Iterable)
            else None
        )

        normalized_length = int(max_length) if isinstance(max_length, (int, float)) else None
        normalized_count = int(max_count) if isinstance(max_count, (int, float)) else None

        return QualityConfig(
            required_categories=required,
            allowed_categories=allowed,
            max_category_length=normalized_length,
            max_categories_per_report=normalized_count,
        )


def reset_caches() -> None:
    """Clear cached boto3 clients and configuration (used by integration tests)."""

    global _CLIENT_CACHE
    global _CONFIG_CACHE
    _CLIENT_CACHE = {}
    _CONFIG_CACHE = None


def _service_endpoint(service: str) -> Optional[str]:
    service_override = os.environ.get(f"{service.upper()}_ENDPOINT_URL")
    if service_override:
        return service_override
    return os.environ.get("AWS_ENDPOINT_URL")


def _client(service: str):
    if service in _CLIENT_CACHE:
        return _CLIENT_CACHE[service]

    region = os.environ.get("AWS_REGION") or os.environ.get("AWS_DEFAULT_REGION") or "us-east-1"
    session = boto3.session.Session(region_name=region)
    endpoint = _service_endpoint(service)

    client = session.client(service, endpoint_url=endpoint)
    _CLIENT_CACHE[service] = client
    return client


def _ensure_env(name: str) -> str:
    value = os.environ.get(name)
    if not value or not value.strip():
        raise RuntimeError(f"Environment variable {name} is required")
    return value.strip()


def _load_quality_config() -> QualityConfig:
    global _CONFIG_CACHE
    if _CONFIG_CACHE is not None:
        return QualityConfig.from_dict(_CONFIG_CACHE)

    bucket = _ensure_env("QUALITY_RULES_BUCKET")
    key = _ensure_env("QUALITY_RULES_KEY")

    s3_client = _client("s3")
    try:
        response = s3_client.get_object(Bucket=bucket, Key=key)
    except ClientError as exc:  # pragma: no cover - exercised via integration test
        LOGGER.error("Failed to download quality rules from s3://%s/%s", bucket, key)
        raise RuntimeError("Unable to load quality rules") from exc

    body = response.get("Body")
    if body is None:
        raise RuntimeError("Quality rules object was empty")

    data = body.read() if hasattr(body, "read") else body
    try:
        parsed = json.loads(data)
    except (TypeError, json.JSONDecodeError) as exc:
        raise RuntimeError("Quality rules file must contain valid JSON") from exc

    if not isinstance(parsed, dict):
        raise RuntimeError("Quality rules file must be a JSON object")

    _CONFIG_CACHE = parsed
    return QualityConfig.from_dict(parsed)


def _normalize_report(report: Dict[str, Any], index: int) -> Dict[str, Any]:
    def ensure_string(value: Any, field: str) -> str:
        if not isinstance(value, str) or not value.strip():
            raise ValueError(f"reports[{index}].{field} must be a non-empty string")
        return value.strip()

    normalized = {
        "reportId": ensure_string(report.get("reportId"), "reportId"),
        "playerId": ensure_string(report.get("playerId"), "playerId"),
        "coachId": ensure_string(report.get("coachId"), "coachId"),
        "reportTimestamp": ensure_string(report.get("reportTimestamp"), "reportTimestamp"),
    }

    categories = report.get("categories")
    if not isinstance(categories, dict) or not categories:
        raise ValueError(f"reports[{index}].categories must be a non-empty object")

    normalized_categories: Dict[str, str] = {}
    for raw_key, raw_value in categories.items():
        if not isinstance(raw_key, str) or not raw_key.strip():
            continue
        key = raw_key.strip()
        value = str(raw_value).strip() if raw_value is not None else ""
        normalized_categories[key] = value

    if not normalized_categories:
        raise ValueError(f"reports[{index}].categories must include valid entries")

    normalized["categories"] = normalized_categories

    optional_fields = ["createdAt", "teamId", "playerEmail", "playerName"]
    for field in optional_fields:
        value = report.get(field)
        if isinstance(value, str) and value.strip():
            normalized[field] = value.strip()

    return normalized


def _validate_reports(event: Dict[str, Any]) -> List[Dict[str, Any]]:
    if not isinstance(event, dict):
        raise ValueError("Event payload must be an object")

    reports = event.get("reports")
    if not isinstance(reports, list) or not reports:
        raise ValueError("Event.reports must be a non-empty array")

    normalized_reports = []
    for index, report in enumerate(reports):
        if not isinstance(report, dict):
            raise ValueError(f"reports[{index}] must be an object")
        normalized_reports.append(_normalize_report(report, index))

    return normalized_reports


def _apply_quality_rules(config: QualityConfig, reports: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    failures: List[Dict[str, Any]] = []
    seen = set()

    for report in reports:
        report_id = report["reportId"]
        issues: List[str] = []

        if report_id in seen:
            issues.append("Duplicate reportId within payload")
        else:
            seen.add(report_id)

        categories = report["categories"]

        if config.required_categories:
            missing = [cat for cat in config.required_categories if cat not in categories or not categories[cat].strip()]
            if missing:
                issues.append("Missing required categories: " + ", ".join(sorted(missing)))

        if config.allowed_categories:
            disallowed = [cat for cat in categories if cat not in config.allowed_categories]
            if disallowed:
                issues.append("Disallowed categories present: " + ", ".join(sorted(disallowed)))

        if config.max_categories_per_report is not None and len(categories) > config.max_categories_per_report:
            issues.append(
                f"Category count {len(categories)} exceeds limit of {config.max_categories_per_report}"
            )

        if config.max_category_length is not None:
            long_fields = [cat for cat, value in categories.items() if len(value) > config.max_category_length]
            if long_fields:
                issues.append(
                    "Category feedback exceeds max length: " + ", ".join(sorted(long_fields))
                )

        if issues:
            failures.append({"reportId": report_id, "issues": issues})

    return failures


def _query_existing_reports(table_name: str, reports: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    dynamodb = _client("dynamodb")
    failures: List[Dict[str, Any]] = []
    inspected: set[str] = set()

    for report in reports:
        report_id = report["reportId"]
        if report_id in inspected:
            continue
        inspected.add(report_id)
        try:
            response = dynamodb.query(
                TableName=table_name,
                IndexName="GSI1",
                KeyConditionExpression="GSI1PK = :pk AND GSI1SK = :sk",
                ExpressionAttributeValues={
                    ":pk": {"S": f"REPORT#{report_id}"},
                    ":sk": {"S": f"REPORT#{report_id}"},
                },
                ProjectionExpression="reportId",
                Limit=1,
            )
        except ClientError as exc:  # pragma: no cover - integration coverage
            LOGGER.error("Failed to query DynamoDB for report %s", report_id)
            raise RuntimeError("Unable to query existing reports") from exc

        if response.get("Count", 0) > 0:
            failures.append(
                {
                    "reportId": report_id,
                    "issues": ["Report already exists in reports table"],
                }
            )

    return failures


def _publish_event(status: str, summary: Dict[str, Any], failures: List[Dict[str, Any]]) -> Optional[str]:
    bus_name = os.environ.get("QUALITY_EVENT_BUS_NAME")
    if not bus_name:
        return None

    event_source = os.environ.get("QUALITY_EVENT_SOURCE", "stat.tracker.quality")
    detail_type = os.environ.get("QUALITY_EVENT_DETAIL_TYPE", "csv.quality")

    events_client = _client("events")
    detail = {
        "status": status,
        "summary": summary,
        "failures": failures,
    }
    response = events_client.put_events(
        Entries=[
            {
                "Source": event_source,
                "DetailType": detail_type,
                "EventBusName": bus_name,
                "Detail": json.dumps(detail),
            }
        ]
    )

    failed = response.get("FailedEntryCount", 0)
    entries = response.get("Entries", [])
    if failed:
        messages = ", ".join(entry.get("ErrorMessage", "unknown error") for entry in entries)
        raise RuntimeError(f"Failed to publish quality event: {messages}")

    return entries[0].get("EventId") if entries else None


def _build_summary(event: Dict[str, Any], reports: List[Dict[str, Any]], failures: List[Dict[str, Any]]) -> Dict[str, Any]:
    summary = {
        "ingestionId": event.get("ingestionId"),
        "sourceBucket": event.get("sourceBucket"),
        "sourceKey": event.get("sourceKey"),
        "totalReports": len(reports),
        "failedReports": len(failures),
    }
    return summary


def handler(event: Dict[str, Any], _context: Any) -> Dict[str, Any]:
    """Validate transformed reports before persistence."""

    config = _load_quality_config()
    reports = _validate_reports(event)

    rule_failures = _apply_quality_rules(config, reports)

    table_name = os.environ.get("REPORTS_TABLE_NAME")
    dynamo_failures: List[Dict[str, Any]] = []
    if table_name:
        dynamo_failures = _query_existing_reports(table_name, reports)

    merged_failures: Dict[str, List[str]] = {}
    for failure in rule_failures + dynamo_failures:
        report_id = failure["reportId"]
        issues = merged_failures.setdefault(report_id, [])
        for issue in failure.get("issues", []):
            if issue not in issues:
                issues.append(issue)

    failures = [
        {"reportId": report_id, "issues": issues}
        for report_id, issues in merged_failures.items()
    ]

    summary = _build_summary(event, reports, failures)
    status = "passed" if not failures else "failed"

    event_id = _publish_event(status, summary, failures)
    if event_id:
        summary["eventBridgeEventId"] = event_id

    if failures:
        summary["status"] = status
        raise QualityCheckError("Quality check failed", failures=failures, summary=summary)

    summary["status"] = status

    enriched_event = dict(event)
    enriched_event["qualityCheck"] = summary
    enriched_event["reports"] = reports
    return enriched_event
