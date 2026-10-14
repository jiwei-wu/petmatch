#!/usr/bin/env bash
# End-to-end demo of PetMatch against a running instance (default http://localhost:8080).
# Needs: curl and python3 (used only to pretty-print JSON and to build timestamps).
set -euo pipefail

BASE="${BASE_URL:-http://localhost:8080}"
RUN="$RANDOM"

# ---------- helpers ----------
json() { python3 -m json.tool; }

# field "<python expression on d>"  reads JSON from stdin, prints the expression
field() { python3 -c "import sys, json; d = json.load(sys.stdin); print($1)"; }

# ago_days 3  ->  timestamp three days ago, in UTC
ago_days() {
  python3 -c "import datetime, sys; t = datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(days=float(sys.argv[1])); print(t.strftime('%Y-%m-%dT%H:%M:%SZ'))" "$1"
}

# register <email> <name>  ->  prints the JWT
register() {
  curl -s -X POST "$BASE/api/auth/register" -H 'Content-Type: application/json' \
    -d "{\"email\":\"$1\",\"password\":\"password123\",\"displayName\":\"$2\"}" | field "d['token']"
}

# api <token> <METHOD> <path> [json body]
api() {
  local token="$1" method="$2" path="$3" body="${4:-}"
  if [ -n "$body" ]; then
    curl -s -X "$method" "$BASE$path" -H "Authorization: Bearer $token" \
      -H 'Content-Type: application/json' -d "$body"
  else
    curl -s -X "$method" "$BASE$path" -H "Authorization: Bearer $token"
  fi
}

# ---------- demo ----------
echo "== 1. Register two users: an owner who lost a cat, and someone who saw one"
OWNER=$(register "owner-$RUN@example.com" "Olive Owner")
FINDER=$(register "finder-$RUN@example.com" "Finn Finder")
echo "   owner-$RUN@example.com and finder-$RUN@example.com registered"

echo
echo "== 2. Olive reports her lost cat (exact location is stored, never returned)"
LOST=$(api "$OWNER" POST /api/posts "{
  \"type\":\"LOST\",\"species\":\"cat\",\"color\":\"orange\",\"size\":\"medium\",
  \"hasCollar\":true,\"description\":\"Friendly, answers to Milo\",
  \"eventTime\":\"$(ago_days 3)\",
  \"latitude\":53.3498,\"longitude\":-6.2603,\"publicArea\":\"Dublin 1\"}")
LOST_ID=$(echo "$LOST" | field "d['id']")
echo "$LOST" | json

echo
echo "== 3. Finn reports a sighting of a similar cat about 200 m away, the next day"
SEEN=$(api "$FINDER" POST /api/posts "{
  \"type\":\"SIGHTING\",\"species\":\"cat\",\"color\":\"ginger\",\"size\":\"medium\",
  \"hasCollar\":true,\"description\":\"Orange cat near the park\",
  \"eventTime\":\"$(ago_days 2)\",
  \"latitude\":53.3516,\"longitude\":-6.2603,\"publicArea\":\"Dublin 1\"}")
SEEN_ID=$(echo "$SEEN" | field "d['id']")
echo "   sighting created: post $SEEN_ID"

echo
echo "== 4. Matching runs in the background; wait for the match to appear"
MATCH_ID=""
for _ in $(seq 1 25); do
    MATCH_ID=$(api "$OWNER" GET /api/matches | field "next((m['matchId'] for m in d if m['myPostId'] == $LOST_ID and m['otherPostId'] == $SEEN_ID), '')")
  [ -n "$MATCH_ID" ] && break
  sleep 0.4
done
if [ -z "$MATCH_ID" ]; then
  echo "No match appeared. Is the app running, and is the sighting within 10 km and 30 days?"
  exit 1
fi
echo "   match $MATCH_ID found"

echo
echo "== 5. Olive's matches: score, explainable breakdown, and only PUBLIC fields of the other post"
api "$OWNER" GET /api/matches | json

echo
echo "== 6. Olive's candidate list for her post (distance is only a coarse band)"
api "$OWNER" GET "/api/matching/posts/$LOST_ID/candidates" | json

echo
echo "== 7. Both people were notified (each about their OWN post only)"
echo "-- Olive:"
api "$OWNER" GET /api/notifications | json
echo "-- Finn:"
api "$FINDER" GET /api/notifications | json

echo
echo "== 8. Finn asks to get in touch. Nobody's email is visible yet."
REQ=$(api "$FINDER" POST "/api/matches/$MATCH_ID/contact-requests" \
  '{"message":"I think I saw Milo near the park yesterday evening."}')
REQ_ID=$(echo "$REQ" | field "d['id']")
echo "$REQ" | json

echo
echo "== 9. Olive's view of the request: still PENDING, contactEmail is null"
api "$OWNER" GET /api/contact-requests | json

echo
echo "== 10. Olive accepts"
api "$OWNER" POST "/api/contact-requests/$REQ_ID/accept" > /dev/null
echo "   accepted"

echo
echo "== 11. Now, and only now, each side sees the other's email"
echo "-- Finn sees:"
api "$FINDER" GET /api/contact-requests | json
echo "-- Olive sees:"
api "$OWNER" GET /api/contact-requests | json

echo
echo "== 12. What the public sees of Olive's post: no coordinates, no user id"
curl -s "$BASE/api/posts/$LOST_ID" | json