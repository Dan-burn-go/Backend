package com.danburn.congestion.repository;

import com.danburn.congestion.dto.CongestionRedisDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class CongestionRedisRepositoryImplTest {

    @Mock
    private RedisTemplate<String, CongestionRedisDto> congestionRedisTemplate;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, CongestionRedisDto> valueOps;

    @Mock
    private ZSetOperations<String, String> zSetOps;

    private CongestionRedisRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new CongestionRedisRepositoryImpl(congestionRedisTemplate, stringRedisTemplate);
    }

    private CongestionRedisDto dto(String areaCode) {
        return new CongestionRedisDto(
                "명동", areaCode, "붐빔", "메시지",
                30000, 34000, "2026-04-01 14:00", List.of()
        );
    }

    @Nested
    @DisplayName("save")
    class Save {

        @Test
        @DisplayName("단건 저장 → opsForValue().set 호출")
        void save() {
            CongestionRedisDto item = dto("POI001");
            given(congestionRedisTemplate.opsForValue()).willReturn(valueOps);
            given(stringRedisTemplate.opsForZSet()).willReturn(zSetOps);

            repository.save(item);

            then(valueOps).should().set(eq("congestion:dto:POI001"), eq(item), eq(15L), any());
        }
    }

    @Nested
    @DisplayName("findByAreaCode")
    class FindByAreaCode {

        @Test
        @DisplayName("캐시 히트 → Optional에 값 포함")
        void hit() {
            CongestionRedisDto item = dto("POI001");
            given(congestionRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.get("congestion:dto:POI001")).willReturn(item);

            Optional<CongestionRedisDto> result = repository.findByAreaCode("POI001");

            assertThat(result).contains(item);
        }

        @Test
        @DisplayName("캐시 미스 → Optional.empty")
        void miss() {
            given(congestionRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.get("congestion:dto:POI999")).willReturn(null);

            Optional<CongestionRedisDto> result = repository.findByAreaCode("POI999");

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("findAllByAreaCodes")
    class FindAllByAreaCodes {

        @Test
        @DisplayName("키 목록으로 multiGet 호출")
        void multiGet() {
            CongestionRedisDto item = dto("POI001");
            given(congestionRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.multiGet(anyList())).willReturn(List.of(item));

            List<CongestionRedisDto> result = repository.findAllByAreaCodes(List.of("POI001"));

            assertThat(result).containsExactly(item);
        }

        @Test
        @DisplayName("multiGet null 반환 시 → size만큼 null로 채워진 목록")
        void multiGetNull() {
            given(congestionRedisTemplate.opsForValue()).willReturn(valueOps);
            given(valueOps.multiGet(anyList())).willReturn(null);

            List<CongestionRedisDto> result = repository.findAllByAreaCodes(List.of("POI001", "POI002"));

            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isNull();
            assertThat(result.get(1)).isNull();
        }
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        @DisplayName("키 없으면 빈 목록")
        void emptyWhenNoKeys() {
            given(stringRedisTemplate.opsForZSet()).willReturn(zSetOps);
            given(zSetOps.rangeByScore(eq("congestion:area_codes"), anyDouble(), anyDouble())).willReturn(Collections.emptySet());

            List<CongestionRedisDto> result = repository.findAll();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("areaCode → key 변환 후 delete 호출")
        void delete() {
            given(stringRedisTemplate.opsForZSet()).willReturn(zSetOps);

            repository.delete("POI001");

            then(congestionRedisTemplate).should().delete("congestion:dto:POI001");
        }
    }
}
