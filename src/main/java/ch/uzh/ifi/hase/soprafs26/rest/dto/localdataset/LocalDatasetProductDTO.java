package ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset;

import java.util.List;
import java.util.Map;

public class LocalDatasetProductDTO {
  private Long productIndex;

  private String barcode;
  private String name;
  private String brand;
  private String imageUrl;

  private String productQuantity;
  private String productQuantityUnit;

  private Double packageQuantity;
  private String packageQuantityUnit;

  private Double servingQuantity;
  private String servingQuantityUnit;

  private NutritionDTO nutrition;
  private List<ConsumptionOptionDTO> consumptionOptions;

  private String dataSource;

  public Long getProductIndex() { return productIndex; }
  public void setProductIndex(Long productIndex) { this.productIndex = productIndex; }

  public String getBarcode() { return barcode; }
  public void setBarcode(String barcode) { this.barcode = barcode; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getBrand() { return brand; }
  public void setBrand(String brand) { this.brand = brand; }

  public String getImageUrl() { return imageUrl; }
  public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

  public String getProductQuantity() { return productQuantity; }
  public void setProductQuantity(String productQuantity) { this.productQuantity = productQuantity; }

  public String getProductQuantityUnit() { return productQuantityUnit; }
  public void setProductQuantityUnit(String productQuantityUnit) { this.productQuantityUnit = productQuantityUnit; }

  public Double getPackageQuantity() { return packageQuantity; }
  public void setPackageQuantity(Double packageQuantity) { this.packageQuantity = packageQuantity; }

  public String getPackageQuantityUnit() { return packageQuantityUnit; }
  public void setPackageQuantityUnit(String packageQuantityUnit) { this.packageQuantityUnit = packageQuantityUnit; }

  public Double getServingQuantity() { return servingQuantity; }
  public void setServingQuantity(Double servingQuantity) { this.servingQuantity = servingQuantity; }

  public String getServingQuantityUnit() { return servingQuantityUnit; }
  public void setServingQuantityUnit(String servingQuantityUnit) { this.servingQuantityUnit = servingQuantityUnit; }

  public NutritionDTO getNutrition() { return nutrition; }
  public void setNutrition(NutritionDTO nutrition) { this.nutrition = nutrition; }

  public List<ConsumptionOptionDTO> getConsumptionOptions() { return consumptionOptions; }
  public void setConsumptionOptions(List<ConsumptionOptionDTO> consumptionOptions) {
    this.consumptionOptions = consumptionOptions;
  }

  public String getDataSource() { return dataSource; }
  public void setDataSource(String dataSource) { this.dataSource = dataSource; }

  public static class NutritionDTO {
    private Double basisAmount;
    private String basisUnit;

    private Map<String, NutrientAmountDTO> coreNutrition;
    private Map<String, NutrientAmountDTO> micronutrients;

    public Double getBasisAmount() { return basisAmount; }
    public void setBasisAmount(Double basisAmount) { this.basisAmount = basisAmount; }

    public String getBasisUnit() { return basisUnit; }
    public void setBasisUnit(String basisUnit) { this.basisUnit = basisUnit; }

    public Map<String, NutrientAmountDTO> getCoreNutrition() { return coreNutrition; }
    public void setCoreNutrition(Map<String, NutrientAmountDTO> coreNutrition) {
      this.coreNutrition = coreNutrition;
    }

    public Map<String, NutrientAmountDTO> getMicronutrients() { return micronutrients; }
    public void setMicronutrients(Map<String, NutrientAmountDTO> micronutrients) {
      this.micronutrients = micronutrients;
    }
  }

  public static class NutrientAmountDTO {
    private Double value;
    private String unit;

    public NutrientAmountDTO() {
    }

    public NutrientAmountDTO(Double value, String unit) {
      this.value = value;
      this.unit = unit;
    }

    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
  }

  public static class ConsumptionOptionDTO {
    private String type;
    private String label;
    private String unit;

    public ConsumptionOptionDTO() {
    }

    public ConsumptionOptionDTO(String type, String label, String unit) {
      this.type = type;
      this.label = label;
      this.unit = unit;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
  }
}
