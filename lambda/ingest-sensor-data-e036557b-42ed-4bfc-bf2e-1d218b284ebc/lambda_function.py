import json
import pymysql
from datetime import datetime

# 하드코딩된 DB 접속 정보
RDS_HOST = "kepcomcs-new.cnqewaqwsk7j.ap-northeast-2.rds.amazonaws.com"
RDS_DB_NAME = "kepcomcs_new"
RDS_USER = "dgu_admin"
RDS_PASSWORD = "wjdtmdghks!23$"

# 전역 DB 연결 객체 (cold start 후에도 재사용 가능)
conn = None
FALLBACK_SEC = 1800 

def lambda_handler(event, context):
    global conn
    try:
        # (A) 커넥션 재사용
        if conn is None:
            conn = pymysql.connect(
                host=RDS_HOST,
                user=RDS_USER,
                password=RDS_PASSWORD,
                db=RDS_DB_NAME,
                charset="utf8mb4",
                cursorclass=pymysql.cursors.DictCursor,
                autocommit=False          # 트랜잭션 수동 커밋
            )
            print("✅ New RDS connection created")
        else:
            print("♻️ Reusing existing RDS connection")

        with conn.cursor() as cur:
            for rec in event["Records"]:
                try:
                    body = json.loads(rec["body"])
                    msg_type      = body.get("msg_type", "DATA")
                    household_id  = int(body["household_id"])
                    sensor_gbn    = body["device_no"]            # 01~04
                    ts_iso        = body["timestamp"].replace("Z", "+00:00")
                    recorded_at   = datetime.fromisoformat(ts_iso)
                    mask          = body["status"]               # "101"
                    led, occ, noi = map(int, mask)

                    # ────────────────────
                    # 2-1. DATA 메시지
                    # ────────────────────
                    if msg_type.upper() == "DATA":
                        cur.execute(
                            """
                            INSERT INTO all_household_sensor_log
                              (household_id, recorded_at,
                               led_sensor_gbn, ocpy_sensor_gbn, noise_sensor_gbn)
                            VALUES (%s, %s, %s, %s, %s)
                            """,
                            (household_id, recorded_at,
                             sensor_gbn if led else None,
                             sensor_gbn if occ else None,
                             sensor_gbn if noi else None)
                        )
                        print(f"📥 DATA logged: hh={household_id}, room={sensor_gbn}, mask={mask}")

                    # ────────────────────
                    # 2-2. FAULT 메시지
                    # ────────────────────
                    elif msg_type.upper() == "FAULT":
                        cur.execute(
                            """
                            INSERT INTO sensor_fault_log
                              (household_id, sensor_gbn,
                               led_fault, occ_fault, noi_fault, recorded_at)
                            VALUES (%s,%s,%s,%s,%s,%s)
                            ON DUPLICATE KEY UPDATE
                              recorded_at = IF(
                                led_fault <> VALUES(led_fault) OR
                                occ_fault <> VALUES(occ_fault) OR
                                noi_fault <> VALUES(noi_fault) OR
                                TIMESTAMPDIFF(SECOND, recorded_at,
                                               VALUES(recorded_at)) > %s,
                                VALUES(recorded_at),
                                recorded_at
                              ),
                              led_fault  = VALUES(led_fault),
                              occ_fault  = VALUES(occ_fault),
                              noi_fault  = VALUES(noi_fault)
                            """,
                            (household_id, sensor_gbn,
                             led, occ, noi, recorded_at,
                             FALLBACK_SEC)
                        )
                        print(f"⚠️  FAULT upserted: hh={household_id}, room={sensor_gbn}, mask={mask}")

                    else:
                        print(f"❓ Unknown msg_type '{msg_type}' → skip")

                except Exception as inner_err:
                    print("❌ Failed to process record:", inner_err)

            conn.commit()
        return { "statusCode": 200, "body": "Records processed" }

    except Exception as e:
        print("❌ Top-level DB error:", e)
        return { "statusCode": 500, "body": str(e) }