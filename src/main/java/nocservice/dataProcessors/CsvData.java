package nocservice.dataProcessors;

import lombok.Data;

@Data
public class CsvData {
    String province;
    String program;
    String employer;
    String city;
    String postalCode;
    String fullNoc;

    public CsvData(String province, String program, String employer, String city, String postalCode, String fullNoc){
        this.province = province;
        this.program = program;
        this.employer = employer;
        this.city = city;
        this.postalCode = postalCode;
        this.fullNoc = fullNoc;
    }
}
