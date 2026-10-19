package com.aibot.travelplanner.trip;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TripListTests {
    @Test
    void emptyDatabaseReturnsAnEmptyPage() {
        var context = org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test_fixture",null,java.util.List.of()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(context);
        var repository = mock(TripRepository.class);
        var pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        when(repository.findByOwnerName("test_fixture",pageable)).thenReturn(Page.empty(pageable));
        var result = new TripService(repository).list(0, 10);
        assertThat(result.trips()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.hasNext()).isFalse();
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }
}
