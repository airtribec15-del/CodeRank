package com.coderank.problem.service;

import com.coderank.common.exception.InvalidRequestException;
import com.coderank.problem.dto.request.CreateCompanyRequest;
import com.coderank.problem.dto.response.CompanyResponse;
import com.coderank.problem.entity.Company;
import com.coderank.problem.mapper.ProblemMapper;
import com.coderank.problem.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final ProblemMapper problemMapper;

    @Transactional(readOnly = true)
    public List<CompanyResponse> getAllCompanies() {
        return companyRepository.findAll().stream()
                .map(problemMapper::toCompanyResponse)
                .toList();
    }

    @Transactional
    public CompanyResponse createCompany(CreateCompanyRequest request) {
        if (companyRepository.existsByNameIgnoreCase(request.getName())) {
            throw new InvalidRequestException("Company already exists: " + request.getName());
        }
        Company company = Company.builder()
                .name(request.getName().trim())
                .build();
        Company saved = companyRepository.save(company);
        log.info("Company created: {}", saved.getName());
        return problemMapper.toCompanyResponse(saved);
    }

    @Transactional
    public void deleteCompany(UUID id) {
        if (!companyRepository.existsById(id)) {
            throw new InvalidRequestException("Company not found: " + id);
        }
        companyRepository.deleteById(id);
        log.info("Company deleted: {}", id);
    }
}