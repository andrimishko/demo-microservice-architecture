#!/bin/sh
# Stage-4 TEST HELPER (runs inside nats-box): publishes a feedback.submitted event by hand until S5 exists.
# Vector: $VECTOR (JSON {"vector":[..],"feature_names":[..]}) or, by default, the latest node-1 window with warn_ratio >= 0.6.
N="nats -s nats://nats:4222"
VERDICT=${VERDICT:-FALSE_POSITIVE}
if [ -n "$VECTOR" ]; then
  PAYLOAD="$VECTOR"
else
  i=$($N stream info DETECTION -j | jq .state.last_seq)
  while [ "$i" -gt 0 ] && [ -z "$PAYLOAD" ]; do
    PAYLOAD=$($N stream get DETECTION "$i" -j | jq -r 'select(.subject=="features.built") | .data' | base64 -d 2>/dev/null \
      | jq -c '.payload | select(.node_id=="node-1" and .vector[(.feature_names|index("warn_ratio"))] >= 0.6)')
    i=$((i-1))
  done
fi
[ -n "$PAYLOAD" ] || { echo "no NOISY_LOGS vector found: switch node-1 to NOISY_LOGS first or pass VECTOR='...'"; exit 1; }
EVENT=$(jq -nc --argjson p "$PAYLOAD" --arg v "$VERDICT" --arg id "$(cat /proc/sys/kernel/random/uuid)" '{
  event_id: $id, event_type: "feedback.submitted", occurred_at: (now | todate), schema_version: 1,
  payload: {feedback_id: $id, analysis_id: "fake-stage4", node_id: ($p.node_id // "node-1"), verdict: $v,
            cause: "stage-4 test helper", vector: $p.vector, feature_names: $p.feature_names}}')
echo "$EVENT"
$N pub feedback.submitted "$EVENT"
