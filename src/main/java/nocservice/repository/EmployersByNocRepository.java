package nocservice.repository;

import nocservice.model.EmployersByNoc;

import java.util.List;

public interface EmployersByNocRepository {
    int save(EmployersByNoc employersByNoc);

    List<EmployersByNoc> findAll();

    List<EmployersByNoc> findByNoc(String noc);

    List<EmployersByNoc> findByEmployerContaining(String employer);

    int deleteAll();
}
