#!/bin/sh
# Verify MinIO server-side stale multipart cleanup before production rollout.
# Usage: MINIO_ALIAS=prod ./deploy/check-minio.sh
set -eu

: "${MINIO_ALIAS:?Set MINIO_ALIAS to an mc alias with admin access}"

config=$(mc admin config get "$MINIO_ALIAS" api)
expiry=
interval=
for item in $config; do
  case "$item" in
    stale_uploads_expiry=*) expiry=${item#*=} ;;
    stale_uploads_cleanup_interval=*) interval=${item#*=} ;;
  esac
done

if [ -z "$expiry" ] || [ -z "$interval" ]; then
  echo "FAIL: stale upload settings are missing from MinIO api config" >&2
  exit 1
fi

to_seconds() {
  value=$1
  case "$value" in
    *d) echo $(( ${value%d} * 86400 )) ;;
    *h) echo $(( ${value%h} * 3600 )) ;;
    *m) echo $(( ${value%m} * 60 )) ;;
    *s) echo "${value%s}" ;;
    *) echo 0 ;;
  esac
}

expiry_seconds=$(to_seconds "$expiry")
if [ "$expiry_seconds" -lt 1 ] || [ "$expiry_seconds" -gt 86400 ]; then
  echo "FAIL: stale_uploads_expiry=$expiry must be between 1 second and 24h" >&2
  exit 1
fi

echo "OK: stale_uploads_expiry=$expiry, stale_uploads_cleanup_interval=$interval"
