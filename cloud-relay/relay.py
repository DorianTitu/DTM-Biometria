import json
import logging
import os
import time
from datetime import datetime, timezone

import pika
import requests

logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"), format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("cloud-relay")

QUEUE = os.getenv("RABBITMQ_QUEUE", "zkteco.auth.events")
API_URL = os.getenv("CLOUD_API_URL", "http://admin-api:8000").rstrip("/") + "/api/attendance/events"
API_KEY = os.getenv("CLOUD_API_KEY", "change-this-listener-key")


def connect():
    credentials = pika.PlainCredentials(os.getenv("RABBITMQ_USERNAME", "guest"), os.getenv("RABBITMQ_PASSWORD", "guest"))
    params = pika.ConnectionParameters(
        host=os.getenv("RABBITMQ_HOST", "rabbitmq"),
        port=int(os.getenv("RABBITMQ_PORT", "5672")),
        virtual_host=os.getenv("RABBITMQ_VHOST", "/"),
        credentials=credentials,
        heartbeat=30,
        blocked_connection_timeout=30,
    )
    return pika.BlockingConnection(params)


def forward(event):
    occurred_at = event.get("authenticatedAt")
    # Jackson serializes OffsetDateTime as epoch seconds in the listener event.
    if isinstance(occurred_at, (int, float)):
        occurred_at = datetime.fromtimestamp(occurred_at, tz=timezone.utc).isoformat()
    payload = {
        "biometric_id": str(event.get("userId", "")).strip(),
        "occurred_at": occurred_at,
        "event_type": "EXIT" if int(event.get("status", 0)) == 1 else "ENTRY",
        "device_id": event.get("deviceSerial"),
        "source_event_id": f"{event.get('deviceSerial')}:{event.get('eventId')}:{event.get('userId')}",
        "verify_type": event.get("verifyType", 0),
        "device_status": event.get("status", 0),
    }
    response = requests.post(API_URL, json=payload, headers={"X-Listener-Key": API_KEY}, timeout=10)
    if response.status_code not in (200, 201, 409):
        raise RuntimeError(f"cloud API returned {response.status_code}: {response.text[:300]}")
    return payload


def run():
    while True:
        try:
            connection = connect()
            channel = connection.channel()
            channel.queue_declare(queue=QUEUE, durable=True)
            channel.basic_qos(prefetch_count=10)

            def consume(ch, method, properties, body):
                try:
                    event = json.loads(body)
                    payload = forward(event)
                    ch.basic_ack(delivery_tag=method.delivery_tag)
                    log.info("[CLOUD_OK] device=%s biometric_id=%s source_event_id=%s", payload["device_id"], payload["biometric_id"], payload["source_event_id"])
                except Exception as exc:
                    log.error("[CLOUD_RETRY] %s event=%r", exc, event)
                    ch.basic_nack(delivery_tag=method.delivery_tag, requeue=True)
                    time.sleep(5)

            channel.basic_consume(queue=QUEUE, on_message_callback=consume)
            log.info("Relay conectado. queue=%s api=%s", QUEUE, API_URL)
            channel.start_consuming()
        except Exception as exc:
            log.error("Relay desconectado: %s. Reintentando en 5s", exc)
            time.sleep(5)


if __name__ == "__main__":
    run()
