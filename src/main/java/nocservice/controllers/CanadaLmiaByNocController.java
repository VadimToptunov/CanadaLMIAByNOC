package nocservice.controllers;

import nocservice.model.EmployersByNoc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import nocservice.repository.JdbcEmployersByNocRepository;

import java.util.ArrayList;
import java.util.List;

@CrossOrigin(origins = "http://localhost:8081")
@RestController
@RequestMapping("/api")
public class CanadaLmiaByNocController {

    @Autowired
    JdbcEmployersByNocRepository jdbcEmployersByNocRepository;

    @GetMapping("/employers/{noc}")
    public ResponseEntity<List<EmployersByNoc>> getEmployersByNoc(@PathVariable("noc") String noc) {
            try {

                if (noc != null) {
                    List<EmployersByNoc> employersByNocList = new ArrayList<>(jdbcEmployersByNocRepository.findByNoc(noc));
                    return new ResponseEntity<>(employersByNocList, HttpStatus.OK);
                }else{
                    return new ResponseEntity<>(HttpStatus.NO_CONTENT);
                }
            } catch (Exception e) {
                ArrayList<EmployersByNoc> dummy = new ArrayList<>();
                dummy.add(new EmployersByNoc("No employer", "No noc"));
                return new ResponseEntity<>(dummy, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }