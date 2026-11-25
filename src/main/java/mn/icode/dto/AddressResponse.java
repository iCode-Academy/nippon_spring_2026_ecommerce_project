package mn.icode.dto;

public class AddressResponse {

    private Long id;
    private String title;
    private String city;
    private String district;
    private String addressLine;
    private String phone;
    private Boolean defaultAddress;

    public AddressResponse() {}

    public AddressResponse(Long id, String title, String city, String district, String addressLine, String phone, Boolean defaultAddress) {
        this.id = id;
        this.title = title;
        this.city = city;
        this.district = district;
        this.addressLine = addressLine;
        this.phone = phone;
        this.defaultAddress = defaultAddress;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }

    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }

    public String getAddressLine() { return addressLine; }
    public void setAddressLine(String addressLine) { this.addressLine = addressLine; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Boolean getDefaultAddress() { return defaultAddress; }
    public void setDefaultAddress(Boolean defaultAddress) { this.defaultAddress = defaultAddress; }
}