package com.coderank.problem.service;

import com.coderank.common.exception.InvalidRequestException;
import com.coderank.problem.dto.request.CreateCompanyRequest;
import com.coderank.problem.dto.response.CompanyResponse;
import com.coderank.problem.entity.Company;
import com.coderank.problem.mapper.ProblemMapper;
import com.coderank.problem.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CompanyService")
class CompanyServiceTest {

    @Mock private CompanyRepository companyRepository;
    @Mock private ProblemMapper problemMapper;

    @InjectMocks private CompanyService companyService;

    private Company company;
    private CompanyResponse companyResponse;

    @BeforeEach
    void setUp() {
        company = Company.builder()
                .id(UUID.randomUUID())
                .name("Google")
                .build();

        companyResponse = CompanyResponse.builder()
                .id(company.getId())
                .name("Google")
                .build();
    }

    // ------------------------------------------------------------------ //
    //  getAllCompanies                                                      //
    // ------------------------------------------------------------------ //
    @Nested
    @DisplayName("getAllCompanies")
    class GetAllCompanies {

        @Test
        @DisplayName("returns list mapped from all repository companies")
        void shouldReturnMappedCompanies() {
            when(companyRepository.findAll()).thenReturn(List.of(company));
            when(problemMapper.toCompanyResponse(company)).thenReturn(companyResponse);

            List<CompanyResponse> result = companyService.getAllCompanies();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("Google");
        }

        @Test
        @DisplayName("returns empty list when repository returns nothing")
        void shouldReturnEmptyWhenNone() {
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
        @DisplayName("saves company and returns mapped response")
        void shouldSaveAndReturnResponse() {
            CreateCompanyRequest request = new CreateCompanyRequest("Google");

            when(companyRepository.existsByNameIgnoreCase("Google")).thenReturn(false);
            when(companyRepository.save(any(Company.class))).thenReturn(company);
            when(problemMapper.toCompanyResponse(company)).thenReturn(companyResponse);

            CompanyResponse result = companyService.createCompany(request);

            assertThat(result.getName()).isEqualTo("Google");
            verify(companyRepository).save(any(Company.class));
        }

        @Test
        @DisplayName("trims whitespace from name before saving")
        void shouldTrimNameBeforeSaving() {
            CreateCompanyRequest request = new CreateCompanyRequest("  Google  ");

            when(companyRepository.existsByNameIgnoreCase("  Google  ")).thenReturn(false);
            when(companyRepository.save(any(Company.class))).thenReturn(company);
            when(problemMapper.toCompanyResponse(company)).thenReturn(companyResponse);

            ArgumentCaptor<Company> captor = ArgumentCaptor.forClass(Company.class);
            companyService.createCompany(request);

            verify(companyRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("Google");
        }

        @Test
        @DisplayName("throws InvalidRequestException containing name when company already exists")
        void shouldThrowWhenAlreadyExists() {
            CreateCompanyRequest request = new CreateCompanyRequest("Google");

            when(companyRepository.existsByNameIgnoreCase("Google")).thenReturn(true);

            assertThatThrownBy(() -> companyService.createCompany(request))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Google");

            verify(companyRepository, never()).save(any());
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
        void shouldCallDeleteByIdWhenExists() {
            UUID id = company.getId();
            when(companyRepository.existsById(id)).thenReturn(true);

            companyService.deleteCompany(id);

            verify(companyRepository).deleteById(id);
        }

        @Test
        @DisplayName("throws InvalidRequestException and never deletes when not found")
        void shouldThrowAndNeverDeleteWhenNotFound() {
            UUID id = UUID.randomUUID();
            when(companyRepository.existsById(id)).thenReturn(false);

            assertThatThrownBy(() -> companyService.deleteCompany(id))
                    .isInstanceOf(InvalidRequestException.class)
                    .hasMessageContaining("Company not found");

            verify(companyRepository, never()).deleteById(any());
        }
    }
}