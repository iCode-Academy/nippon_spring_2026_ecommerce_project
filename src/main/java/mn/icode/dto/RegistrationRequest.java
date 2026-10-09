package mn.icode.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistrationRequest {
    
    @NotBlank(message="First name is required")
    @Size(max=100, message="First name must not exceed 100 characters")
    private String firstName;

    @NotBlank(message="Last name is required")
    @Size(max=100, message="Last name must not exceed 100 characters")
    private String lastName;

    @NotBlank(message="Email is required")
    @Email(message="Email must be valid")
    private String email;

    @NotBlank(message="Password is required")
    private String password;

    public String phone;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }



}
