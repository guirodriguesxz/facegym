#!/usr/bin/env bash
# scripts/seed-demo.sh — popula a API com os 5 alunos da demo. Idempotente.
# Uso: API=https://.../api/v1 ADMIN_EMAIL=... ADMIN_PASSWORD=... scripts/seed-demo.sh
set -euo pipefail
API=${API:-http://localhost:8081/api/v1}
ADMIN_EMAIL=${ADMIN_EMAIL:-admin@facegym.dev}
: "${ADMIN_PASSWORD:?defina ADMIN_PASSWORD}"
DIR=$(cd "$(dirname "$0")" && pwd)

j() { curl -sS -H 'Content-Type: application/json' "$@"; }
T=$(j -X POST "$API/auth/login" -d "{\"email\":\"$ADMIN_EMAIL\",\"senha\":\"$ADMIN_PASSWORD\"}" | sed -E 's/.*"token":"([^"]+)".*/\1/')
A="Authorization: Bearer $T"
id() { sed -E 's/.*"id":"([^"]+)".*/\1/'; }

plano() { # nome, dias, inicio, fim, limite
  local existente
  existente=$(curl -sS -H "$A" "$API/planos" | python3 -c "import sys,json; print(next((p['id'] for p in json.load(sys.stdin) if p['nome']=='$1'), ''))")
  if [ -n "$existente" ]; then echo "$existente"; return; fi
  j -H "$A" -X POST "$API/planos" -d "{\"nome\":\"$1\",\"preco\":99.9,\"dias\":$2,\"inicio\":\"$3\",\"fim\":\"$4\",\"acessosPorSemana\":$5}" | id
}
TODOS='["MONDAY","TUESDAY","WEDNESDAY","THURSDAY","FRIDAY","SATURDAY","SUNDAY"]'
LIVRE=$(plano "Livre" "$TODOS" "00:00" "23:59" null)
MADRUGADA=$(plano "Madrugada" "$TODOS" "03:00" "04:00" null)
TRES=$(plano "3x por semana" "$TODOS" "00:00" "23:59" 3)

HOJE=$(date +%F)
ONTEM=$(date -v-1d +%F 2>/dev/null || date -d yesterday +%F)
INICIO=$(date -v-60d +%F 2>/dev/null || date -d '60 days ago' +%F)
FIM=$(date -v+365d +%F 2>/dev/null || date -d '365 days' +%F)

aluno() { # slug, nome, cpf, plano, vencimento
  local existente
  existente=$(curl -sS -H "$A" "$API/alunos" | python3 -c "import sys,json; print(next((a['id'] for a in json.load(sys.stdin) if a['cpf']=='$3'), ''))")
  if [ -n "$existente" ]; then echo "$existente"; return; fi
  local ID
  ID=$(j -H "$A" -X POST "$API/alunos" -d "{\"nome\":\"$2\",\"cpf\":\"$3\"}" | id)
  j -H "$A" -X POST "$API/matriculas" -d "{\"alunoId\":\"$ID\",\"planoId\":\"$4\",\"inicio\":\"$INICIO\",\"vencimento\":\"$5\"}" >/dev/null
  curl -sS -H "$A" -X POST "$API/alunos/$ID/consentimento" >/dev/null
  curl -sS -f -H "$A" -X PUT "$API/alunos/$ID/biometria" -F foto=@"$DIR/demo-fotos/$1.jpg" >/dev/null
  echo "$ID"
}

aluno ana   "Ana Souza"    "52998224725" "$LIVRE"     "$FIM"   >/dev/null
aluno bruno "Bruno Lima"   "11144477735" "$LIVRE"     "$ONTEM" >/dev/null
aluno carla "Carla Mendes" "39053344705" "$MADRUGADA" "$FIM"   >/dev/null
aluno diego "Diego Rocha"  "86288366757" "$TRES"      "$FIM"   >/dev/null
ELISA=$(aluno elisa "Elisa Prado" "34608514300" "$LIVRE" "$FIM")
j -H "$A" -X POST "$API/alunos/$ELISA/bloqueio" -d '{"motivo":"Falta de atestado médico"}' -o /dev/null -w ''

# Diego: completa 3 acessos na semana (roda de novo na segunda para manter o cenário)
for _ in 1 2 3; do
  R=$(j -X POST "$API/check-ins/cpf" -d '{"cpf":"86288366757"}')
  echo "$R" | grep -q NEGADO && break
done
echo "Seed concluído."
