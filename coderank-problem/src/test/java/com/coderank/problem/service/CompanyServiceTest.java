package com.coderank.problem.service;

import com.coderank.common.exception.InvalidRequestException;
import com.coderank.problem.dto.request.CreateCompanyRequest;
import com.coderank.problem.dto.response.CompanyResponse;
import com.coderank.problem.entity.Company;
import com.coderank.problem.mapper.ProblemMapper;
import com.coderank.problem.repository.CompanyRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompanyService")
class CompanyServiceTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private ProblemMapper     problemMapper;

    @InjectMocks
    private CompanyService companyService;

    private final UUID    companyId = UUID.randomUUID();
    private       Company company;
    private       CompanyResponse companyResponse;

    @BeforeEach
    void setUp() {
        company = Company.builder().id(companyId).name("Google").build();
        companyResponse = CompanyResponse.builder().id(companyId).name("Google").build();
    }

    // ------------------------------------------------------------------ //
    //  getAllCompanies                                                      //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("getAllCompanies")
    class GetAllCompanies {

        @Test
        @DisplayName("returns mapped list of all companies")
        void shouldReturnAllCompanies() {
            when(companyRepository.findAll()).thenReturn(List.of(company));
            when(problemMapper.toCompanyResponse(company)).thenReturn(companyResponse);

            List<CompanyResponse> result = companyService.getAllCompanies();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Google");
        }

        @Test
        @DisplayName("returns empty list when no companies exist")
        void shouldReturnEmptyListWhenNone() {
            when(companyRepository.findAll()).thenReturn(List.of());

            List<CompanyResponse> result = companyService.getAllCompanies();

            assertThat(result).isEmpty();
        }
    }

    // ------------------------------------------------------------------ //
    //  createCompany                                                       //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("createCompany")
    class CreateCompany {

        @Test
        @DisplayName("creates and returns company response when name is unique")
        void shouldCreateWhenNameIsUnique() {
            when(companyRepository.existsByNameIgnoreCase("Google")).thenReturn(false);
            when(companyRepository.save(any(Company.class))).thenReturn(company);
            when(problemMapper.toCompanyResponse(company)).thenReturn(companyResponse);

            CompanyResponse result = companyService.createCompany(
                    CreateCompanyRequest.builder().name("Google").build());

            assertThat(result.getName()).isEqualTo("Google");
            verify(companyRepository).save(any(Company.class));
        }

        @Test
        @DisplayName("throws InvalidRequestException when company name already exists")
        void shouldThrowWhenDuplicate() {
            when(companyRepository.existsByNameIgnoreCase("Google")).thenReturn(true);

            assertThatThrownBy(() ->
                    companyService.createCompany(
                            CreateCompanyRequest.builder().name("Google").build()))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("already exists");

            verify(companyRepository, never()).save(any());
        }

        @Test
        @DisplayName("name check is case-insensitive")
        void shouldCheckCaseInsensitive() {
            when(companyRepository.existsByNameIgnoreCase("google")).thenReturn(true);

            assertThatThrownBy(() ->
                    companyService.createCompany(
                            CreateCompanyRequest.builder().name("google").build()))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    // ------------------------------------------------------------------ //
    //  deleteCompany                                                       //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("deleteCompany")
    class DeleteCompany {

        @Test
        @DisplayName("calls deleteById when company exists")
        void shouldDeleteWhenExists() {
            when(companyRepository.existsById(companyId)).thenReturn(true);

            companyService.deleteCompany(companyId);

            verify(companyRepository).deleteById(companyId);
        }

        @Test
        @DisplayName("throws and never deletes when company not found")
        void shouldThrowWhenNotFound() {
            when(companyRepository.existsById(companyId)).thenReturn(false);

            assertThatThrownBy(() -> companyService.deleteCompany(companyId))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Company not found");

            verify(companyRepository, never()).deleteById(any());
        }
    }
}