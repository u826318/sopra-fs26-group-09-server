package ch.uzh.ifi.hase.soprafs26.rest.dto.localdataset;

import java.util.ArrayList;
import java.util.List;

public class LocalDatasetProductSearchResponseDTO {
  private String query;
  private String normalizedQuery;
  private String status;
  private String message;
  private Integer totalCandidateCount;
  private List<String> anchorTokens = new ArrayList<>();
  private List<String> auxiliaryTokens = new ArrayList<>();
  private List<LocalDatasetProductSearchCandidateDTO> candidates = new ArrayList<>();

  public String getQuery() { return query; }
  public void setQuery(String query) { this.query = query; }

  public String getNormalizedQuery() { return normalizedQuery; }
  public void setNormalizedQuery(String normalizedQuery) { this.normalizedQuery = normalizedQuery; }

  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }

  public String getMessage() { return message; }
  public void setMessage(String message) { this.message = message; }

  public Integer getTotalCandidateCount() { return totalCandidateCount; }
  public void setTotalCandidateCount(Integer totalCandidateCount) { this.totalCandidateCount = totalCandidateCount; }

  public List<String> getAnchorTokens() { return anchorTokens; }
  public void setAnchorTokens(List<String> anchorTokens) { this.anchorTokens = anchorTokens; }

  public List<String> getAuxiliaryTokens() { return auxiliaryTokens; }
  public void setAuxiliaryTokens(List<String> auxiliaryTokens) { this.auxiliaryTokens = auxiliaryTokens; }

  public List<LocalDatasetProductSearchCandidateDTO> getCandidates() { return candidates; }
  public void setCandidates(List<LocalDatasetProductSearchCandidateDTO> candidates) { this.candidates = candidates; }
}
