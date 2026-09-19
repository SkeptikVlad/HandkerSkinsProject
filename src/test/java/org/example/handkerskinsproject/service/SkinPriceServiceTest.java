package org.example.handkerskinsproject.service;

import org.example.handkerskinsproject.dto.MarketItemPriceDto;
import org.example.handkerskinsproject.dto.MarketPricesResponse;
import org.example.handkerskinsproject.entity.SkinEntity;
import org.example.handkerskinsproject.entity.SkinPriceHistoryEntity;
import org.example.handkerskinsproject.fetcher.MarketCsgoFetcherClient;
import org.example.handkerskinsproject.repository.SkinPriceHistoryRepository;
import org.example.handkerskinsproject.repository.SkinRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SkinPriceServiceTest {

    @Mock private SkinRepository skinRepository;
    @Mock private SkinPriceHistoryRepository historyRepository;
    @Mock private MarketCsgoFetcherClient fetcherClient;

    private SkinPriceService service;

    @BeforeEach
    void setUp() {
        service = new SkinPriceService(skinRepository, historyRepository, fetcherClient);
    }

    @Test
    void syncPrices_withNoTrackedSkins_skipsAndDoesNotCallFetcher() {
        when(skinRepository.findAll()).thenReturn(List.of());

        service.syncPrices();

        verifyNoInteractions(fetcherClient);
        verifyNoInteractions(historyRepository);
    }

    @Test
    void syncPrices_savesHistoryWithPriceVolumeAndTopOrder() {
        SkinEntity skin = new SkinEntity("AK-47 | Redline (Field-Tested)");
        when(skinRepository.findAll()).thenReturn(List.of(skin));
        when(fetcherClient.fetchAllPrices()).thenReturn(new MarketPricesResponse(true, List.of()));
        when(fetcherClient.fetchPricesMap()).thenReturn(Map.of(
                "AK-47 | Redline (Field-Tested)",
                new MarketItemPriceDto("AK-47 | Redline (Field-Tested)", new BigDecimal("1500.00"), 7)
        ));
        when(fetcherClient.fetchTopOrdersMap()).thenReturn(Map.of(
                "AK-47 | Redline (Field-Tested)", new BigDecimal("1400.00")
        ));

        service.syncPrices();

        ArgumentCaptor<SkinPriceHistoryEntity> captor = ArgumentCaptor.forClass(SkinPriceHistoryEntity.class);
        verify(historyRepository).save(captor.capture());

        SkinPriceHistoryEntity saved = captor.getValue();
        assertEquals(skin, saved.getSkin());
        assertEquals(new BigDecimal("1500.00"), saved.getMinPrice());
        assertEquals(7, saved.getVolume());
        assertEquals(new BigDecimal("1400.00"), saved.getTopOrder());
    }

    @Test
    void syncPrices_skinWithoutPriceData_isSkipped() {
        SkinEntity skin = new SkinEntity("Untracked Anywhere | Skin");
        when(skinRepository.findAll()).thenReturn(List.of(skin));
        when(fetcherClient.fetchAllPrices()).thenReturn(new MarketPricesResponse(true, List.of()));
        when(fetcherClient.fetchPricesMap()).thenReturn(Map.of());
        when(fetcherClient.fetchTopOrdersMap()).thenReturn(Map.of());

        service.syncPrices();

        verifyNoInteractions(historyRepository);
    }

    @Test
    void syncPrices_whenFetchAllPricesUnsuccessful_doesNotThrowAndSkipsSaving() {
        when(skinRepository.findAll()).thenReturn(List.of(new SkinEntity("AK-47 | Redline (Field-Tested)")));
        when(fetcherClient.fetchAllPrices()).thenReturn(new MarketPricesResponse(false, null));

        assertDoesNotThrow(() -> service.syncPrices());

        verifyNoInteractions(historyRepository);
    }

    @Test
    void addSkinToTrack_returnsExistingSkinIfAlreadyTracked() {
        SkinEntity existing = new SkinEntity("AK-47 | Redline (Field-Tested)");
        when(skinRepository.findByMarketHashName("AK-47 | Redline (Field-Tested)")).thenReturn(Optional.of(existing));

        SkinEntity result = service.addSkinToTrack("AK-47 | Redline (Field-Tested)");

        assertSame(existing, result);
        verify(skinRepository, never()).save(any());
    }

    @Test
    void addSkinToTrack_createsNewSkinIfNotTracked() {
        when(skinRepository.findByMarketHashName("New Skin")).thenReturn(Optional.empty());
        when(skinRepository.save(any(SkinEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SkinEntity result = service.addSkinToTrack("New Skin");

        assertEquals("New Skin", result.getMarketHashName());
        verify(skinRepository).save(any(SkinEntity.class));
    }
}