package com.geuneul.domain.review;

import com.geuneul.domain.auth.User;
import com.geuneul.domain.auth.UserRepository;
import com.geuneul.domain.place.PlaceRepository;
import com.geuneul.domain.review.dto.ReviewCreateRequest;
import com.geuneul.domain.review.dto.ReviewListResponse;
import com.geuneul.domain.review.dto.ReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * 영구 후기(review) 오케스트레이션. survival_score(휘발성 제보)와 완전히 분리된 파이프라인(§5) —
 * ReportService를 참고 삼되 place_report_signals 뷰·SurvivalScore는 절대 건드리지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final PlaceRepository placeRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public ReviewService(ReviewRepository reviewRepository, PlaceRepository placeRepository,
                         UserRepository userRepository, ObjectMapper objectMapper) {
        this.reviewRepository = reviewRepository;
        this.placeRepository = placeRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 후기 작성/수정. 정책: <b>장소당 1건 upsert</b> — 같은 유저가 같은 장소에 재작성하면
     * 기존 후기를 갱신한다(Review 클래스 주석의 근거 참고). userId는 호출부(컨트롤러)가 JWT에서
     * 뽑아 전달한다 — 요청 바디로 받지 않는다(신원 위조 방지).
     */
    @Transactional
    public ReviewResponse create(long userId, ReviewCreateRequest request) {
        requirePlace(request.placeId());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "user not found: " + userId));

        short rating = (short) request.rating().intValue();
        String comment = normalize(request.comment());
        String photosJson = toJson(request.photos());

        Review review = reviewRepository.findByUserIdAndPlaceId(userId, request.placeId())
                .map(existing -> {
                    existing.updateContent(rating, comment, photosJson);
                    return existing;
                })
                .orElseGet(() -> Review.of(userId, request.placeId(), rating, comment, photosJson));

        Review saved = reviewRepository.save(review);
        return ReviewResponse.of(saved, user.getNickname(), user.getProfileImage(), toPhotos(photosJson));
    }

    /** 장소의 후기 목록 — 공개, 최신순 페이지네이션(작성자 닉네임/프로필 조인). */
    public ReviewListResponse listByPlace(long placeId, int page, int size) {
        requirePlace(placeId);
        Pageable pageable = PageRequest.of(page, size);
        Page<ReviewWithAuthorView> found = reviewRepository.findByPlaceIdWithAuthor(placeId, pageable);
        Page<ReviewResponse> mapped = found.map(v -> ReviewResponse.of(v, toPhotos(v.getPhotosJson())));
        return ReviewListResponse.of(mapped);
    }

    private void requirePlace(long placeId) {
        if (!placeRepository.existsById(placeId)) {
            throw new ResponseStatusException(NOT_FOUND, "place not found: " + placeId);
        }
    }

    private static String normalize(String comment) {
        if (comment == null) return null;
        String trimmed = comment.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** photos → JSONB 저장 문자열. 빈/미제공이면 null(칼럼 nullable). */
    private String toJson(List<String> photos) {
        if (photos == null || photos.isEmpty()) return null;
        return objectMapper.writeValueAsString(photos);
    }

    /** JSONB 문자열 → photos. null/blank면 빈 리스트(응답에서 [] 로 보이게, null 아님). */
    private List<String> toPhotos(String photosJson) {
        if (photosJson == null || photosJson.isBlank()) return List.of();
        return List.of(objectMapper.readValue(photosJson, String[].class));
    }
}
