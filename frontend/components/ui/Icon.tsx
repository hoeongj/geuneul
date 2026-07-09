import { ICON_FILLED, ICONS, type IconName, type IconPrim } from "@/lib/icon-paths";

interface IconProps {
  name: IconName;
  size?: number;
  strokeWidth?: number;
  className?: string;
  /** 인스턴스별 채움 오버라이드(예: 별점의 켜짐/꺼짐 별). 미지정이면 ICON_FILLED(전역) 기본값을 따른다. */
  filled?: boolean;
}

// 디자인 프로토타입 svgIcon() 을 그대로 재현하는 단일 아이콘 시스템.
// color 는 CSS `currentColor` 를 따르므로 부모의 text-* 유틸로 색을 제어한다.
export function Icon({ name, size = 20, strokeWidth = 1.8, className, filled }: IconProps) {
  // IconPrim[]로 넓혀 "path" in p 가드가 circle 멤버의 fill? 을 살리게 한다(satisfies가 좁힌 타입 복원).
  const prims: IconPrim[] = ICONS[name];
  const isFilled = filled ?? ICON_FILLED.has(name);
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill={isFilled ? "currentColor" : "none"}
      stroke={isFilled ? "none" : "currentColor"}
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      aria-hidden="true"
      focusable="false"
    >
      {prims.map((p, i) => {
        if ("path" in p) return <path key={i} d={p.path} />;
        const [cx, cy, r] = p.circle;
        const solid = p.fill || isFilled;
        return (
          <circle
            key={i}
            cx={cx}
            cy={cy}
            r={r}
            fill={solid ? "currentColor" : "none"}
            stroke={solid ? "none" : "currentColor"}
          />
        );
      })}
    </svg>
  );
}
