package nocservice.model;

import lombok.Data;

@Data
public class EmployersByNoc {
    private Long id;
    private String employer;
    private String noc;

    public EmployersByNoc(String employer, String noc) {
        this.employer = employer;
        this.noc = noc;
    }
}
