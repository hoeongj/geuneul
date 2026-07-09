package com.geuneul.domain.weather;

/**
 * 기상청 격자 변환 — WGS84 위경도(lat/lon)를 기상청 단기예보 격자 좌표(nx, ny)로 변환한다.
 *
 * 기상청 예보는 전국을 5km×5km Lambert Conformal Conic 격자로 나눠 제공하므로, 좌표로 조회하려면
 * 먼저 격자 번호로 바꿔야 한다. 아래 상수·수식은 기상청 "동네예보 좌표변환(DFS_XY_CONV)" 공식 그대로다
 * (RE/GRID/기준위도·경도·원점). 서울시청(37.5665, 126.9780) → (nx=60, ny=127)로 검증된다.
 *
 * 순수 함수라 외부 의존이 없다 — 캐시 키(nx,ny)를 만드는 데 쓰고, 같은 격자면 한 번만 조회한다.
 */
public final class KmaGrid {

    private static final double RE = 6371.00877;   // 지구 반경(km)
    private static final double GRID = 5.0;        // 격자 간격(km)
    private static final double SLAT1 = 30.0;      // 표준 위도 1
    private static final double SLAT2 = 60.0;      // 표준 위도 2
    private static final double OLON = 126.0;      // 기준점 경도
    private static final double OLAT = 38.0;       // 기준점 위도
    private static final double XO = 43;           // 기준점 X 격자
    private static final double YO = 136;          // 기준점 Y 격자
    private static final double DEGRAD = Math.PI / 180.0;

    private KmaGrid() {
    }

    /** 격자 좌표(nx, ny). */
    public record Grid(int nx, int ny) {
    }

    public static Grid toGrid(double lat, double lon) {
        double re = RE / GRID;
        double slat1 = SLAT1 * DEGRAD;
        double slat2 = SLAT2 * DEGRAD;
        double olon = OLON * DEGRAD;
        double olat = OLAT * DEGRAD;

        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
        ro = re * sf / Math.pow(ro, sn);

        double ra = Math.tan(Math.PI * 0.25 + lat * DEGRAD * 0.5);
        ra = re * sf / Math.pow(ra, sn);
        double theta = lon * DEGRAD - olon;
        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        }
        if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }
        theta *= sn;

        int nx = (int) Math.floor(ra * Math.sin(theta) + XO + 0.5);
        int ny = (int) Math.floor(ro - ra * Math.cos(theta) + YO + 0.5);
        return new Grid(nx, ny);
    }
}
