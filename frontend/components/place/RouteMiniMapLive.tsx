"use client";

import { Map, MapMarker, Polyline } from "react-kakao-maps-sdk";
import { iconForCategory } from "@/lib/categories";
import { markerImage } from "@/lib/marker";
import type { RouteResult } from "@/types/route";
import type { Category } from "@/types/place";

// 경유지 경로(B2/F3 화장실·C4 그늘) 미니맵: 출발→(경유지)→도착 폴리라인 + 마커.
// mode=road(카카오 도로 경로, 실선 파랑) | straight(직선 폴백, 점선 회색). 폴리라인 전체가 보이게 bounds fit.
export default function RouteMiniMapLive({
  route,
  destCategory,
}: {
  route: RouteResult;
  destCategory: Category;
}) {
  const path = route.polyline.map((p) => ({ lat: p.lat, lng: p.lng }));
  const isRoad = route.mode === "road";

  return (
    <Map
      center={{ lat: route.origin.lat, lng: route.origin.lng }}
      level={5}
      draggable={false}
      zoomable={false}
      className="h-[170px] w-full"
      onCreate={(map) => {
        // 폴리라인 전체가 보이게 bounds fit. kakao 전역은 로더 이후 존재(any 캐스팅으로 타입 회피).
        const w = window as unknown as { kakao?: { maps: { LatLngBounds: new () => { extend: (ll: unknown) => void }; LatLng: new (lat: number, lng: number) => unknown } } };
        if (!w.kakao?.maps || path.length === 0) return;
        const bounds = new w.kakao.maps.LatLngBounds();
        path.forEach((pt) => bounds.extend(new w.kakao!.maps.LatLng(pt.lat, pt.lng)));
        map.setBounds(bounds as never);
      }}
    >
      <Polyline
        path={path}
        strokeWeight={5}
        strokeColor={isRoad ? "#2b6ff6" : "#8a8f98"}
        strokeOpacity={0.9}
        strokeStyle={isRoad ? "solid" : "shortdash"}
      />
      {/* 출발(현재 위치) */}
      <MapMarker position={{ lat: route.origin.lat, lng: route.origin.lng }} image={markerImage("locate", false)} />
      {/* 경유지(화장실 or 쿨링쉼터/실내) — 카테고리별 아이콘(C4). 카테고리 없으면 toilet 폴백(F3 호환). */}
      {route.waypoint && (
        <MapMarker
          position={{ lat: route.waypoint.lat, lng: route.waypoint.lng }}
          image={markerImage(route.waypoint.category ? iconForCategory(route.waypoint.category as Category) : "toilet", true)}
        />
      )}
      {/* 경로 주변 그늘/실내 피난처(F4) — 쉼터·도서관·지하상가 오버레이("더울 때 피할 곳") */}
      {route.shadeSpots.map((s) => (
        <MapMarker
          key={s.placeId}
          position={{ lat: s.lat, lng: s.lng }}
          image={markerImage(iconForCategory(s.category as Category), false)}
          title={s.name}
        />
      ))}
      {/* 도착 */}
      <MapMarker
        position={{ lat: route.destination.lat, lng: route.destination.lng }}
        image={markerImage(iconForCategory(destCategory), false)}
      />
    </Map>
  );
}
