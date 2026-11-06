package mn.icode.dto;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProductImageForm {
    
    @NotBlank(message="Image URL is required")
    @Size(max=500, message="Image URL must not exceed 500 characters")
    @URL(protocol="https", message="Image URL must be a valid HTTPS URL")
    private String imageUrl;

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    
}
