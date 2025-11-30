package nocservice.repository;

import nocservice.model.EmployersByNoc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class JdbcEmployersByNocRepository implements EmployersByNocRepository{
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public int save(EmployersByNoc employersByNoc) {
        return jdbcTemplate.update("INSERT INTO employersbynoc (employer, noc) VALUES(?,?)",
                employersByNoc.getEmployer(), employersByNoc.getNoc());
    }

    @Override
    public List<EmployersByNoc> findAll() {
        return jdbcTemplate.query("SELECT * from employersByNoc", BeanPropertyRowMapper.newInstance(EmployersByNoc.class));
    }

    @Override
    public List<EmployersByNoc> findByNoc(String noc) {
        return jdbcTemplate.query("SELECT * from employersbynoc WHERE noc=?",
                BeanPropertyRowMapper.newInstance(EmployersByNoc.class), noc);
    }

    @Override
    public List<EmployersByNoc> findByEmployerContaining(String employer) {
        String q = "SELECT * from tutorials WHERE employer LIKE '%" + employer + "%'";
        return jdbcTemplate.query(q, BeanPropertyRowMapper.newInstance(EmployersByNoc.class));
    }

    @Override
    public int deleteAll() {
        return jdbcTemplate.update("DELETE FROM employersbynoc");
    }
}
