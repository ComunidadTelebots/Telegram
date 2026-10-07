#!/bin/sh
set -eu
export TELEGRAM_API_ID="$(cat /run/secrets/api_id)"
export TELEGRAM_API_HASH="$(cat /run/secrets/api_hash)"
exec telegram-bot-api --local --http-port=8081 --dir=/var/lib/telegram-bot-api
