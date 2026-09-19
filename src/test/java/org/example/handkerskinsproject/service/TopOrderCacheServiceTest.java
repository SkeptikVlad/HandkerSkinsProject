package org.example.handkerskinsproject.service;

import org.example.handkerskinsproject.entity.SkinEntity;
import org.example.handkerskinsproject.fetcher.MarketCsgoFetcherClient;
import org.example.handkerskinsproject.repository.SkinRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TopOrderCacheServiceTest {

    @Mock private MarketCsgoFetcherClient fetcherClient;
    @Mock private SkinRepository skinRepository;

    private TopOrderCacheService cacheService;

    @BeforeEach
    void setUp() {
        cacheService = new TopOrderCacheService(fetcherClient, skinRepository);
    }

    @Test
    void refresh_withNoTrackedSkins_doesNotCallFetcher() {
        when(skinRepository.findAll()).thenReturn(List.of());

        cacheService.refresh();

        verifyNoInteractions(fetcherClient);
        assertNull(cacheService.getTopOrder("AK-47 | Redline (Field-Tested)"));
    }

    @Test
    void refresh_keepsOnlyTrackedSkinsInCache() {
        when(skinRepository.findAll()).thenReturn(List.of(
                new SkinEntity("AK-47 | Redline (Field-Tested)"),
                new SkinEntity("AWP | Asiimov (Field-Tested)")
        ));
        when(fetcherClient.fetchTopOrdersMap()).thenReturn(Map.of(
                "AK-47 | Redline (Field-Tested)", new BigDecimal("1000"),
                "AWP | Asiimov (Field-Tested)", new BigDecimal("2000"),
                "Untracked Skin", new BigDecimal("500")
        ));

        cacheService.refresh();

        assertEquals(new BigDecimal("1000"), cacheService.getTopOrder("AK-47 | Redline (Field-Tested)"));
        assertEquals(new BigDecimal("2000"), cacheService.getTopOrder("AWP | Asiimov (Field-Tested)"));
        assertNull(cacheService.getTopOrder("Untracked Skin"));
    }

    @Test
    void refresh_whenFetcherThrows_doesNotPropagateException() {
        when(skinRepository.findAll()).thenReturn(List.of(new SkinEntity("AK-47 | Redline (Field-Tested)")));
        when(fetcherClient.fetchTopOrdersMap()).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> cacheService.refresh());
        assertNull(cacheService.getTopOrder("AK-47 | Redline (Field-Tested)"));
    }

    @Test
    void getTopOrder_returnsNullBeforeAnyRefresh() {
        assertNull(cacheService.getTopOrder("Anything"));
    }
}