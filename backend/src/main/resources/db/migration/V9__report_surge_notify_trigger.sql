-- 실시간 제보 급증 알림(ADR-0016)의 전파 트리거 — reports INSERT 시 place_id를 NOTIFY로 흘린다.
--
-- 왜 트리거 + pg_notify인가(ADR-0016 §2): 제보 insert와 "같은 트랜잭션"에서 알림을 큐잉하고 커밋 시점에
-- 전달하므로 유실 창이 없다. 앱 인스턴스들은 'geuneul_report_surge' 채널을 LISTEN 하다가 알림을 받으면
-- 그 장소의 급증 여부(최근 N분 유효제보 ≥ K건)를 앱 레이어에서 재확인해 SSE 구독자에게 민다.
-- ECS 오토스케일링(min1/max3, ADR-0013)에서 insert 인스턴스 ≠ 구독 인스턴스일 수 있어, 인프로세스 이벤트가
-- 아니라 DB 브로드캐스트(LISTEN/NOTIFY)로 전 인스턴스가 알림을 받아야 한다 — 새 인프라 0(이미 있는 RDS).
--
-- 페이로드는 place_id 하나(문자열) — LISTEN/NOTIFY 8000바이트 제한과 무관하고, 급증 재판정은 어차피 DB에서
-- 하므로 트리거가 급증 여부까지 계산하지 않는다(트리거는 가볍게, 판정은 앱이 시공간 SQL로 — 관심사 분리).

CREATE OR REPLACE FUNCTION notify_report_surge() RETURNS trigger AS $$
BEGIN
    -- place_id만 채널로 통지. 유효(미만료) 여부·급증 임계 판정은 수신 측(앱)이 place_report_signals와
    -- 같은 정신모델의 count 쿼리로 재확인한다(ADR-0016 §1). pg_notify는 두 번째 인자가 text라 캐스팅.
    PERFORM pg_notify('geuneul_report_surge', NEW.place_id::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION notify_report_surge() IS
    '제보 INSERT 시 place_id를 geuneul_report_surge 채널로 NOTIFY(ADR-0016 실시간 급증 알림 전파).';

CREATE TRIGGER trg_report_surge_notify
    AFTER INSERT ON reports
    FOR EACH ROW
    EXECUTE FUNCTION notify_report_surge();
