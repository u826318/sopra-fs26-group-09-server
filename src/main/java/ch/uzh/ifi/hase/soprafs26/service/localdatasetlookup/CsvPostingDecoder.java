package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CsvPostingDecoder {

  private static final String DELTA_SPACE_SEPARATED = "delta-space-separated";

  public List<Long> decode(String encoding, String encodedProductIndices) {
    if (encodedProductIndices == null || encodedProductIndices.isBlank()) {
      return List.of();
    }
    if (!DELTA_SPACE_SEPARATED.equals(encoding)) {
      throw new IllegalArgumentException("Unsupported token-posting encoding: " + encoding);
    }

    List<Long> result = new ArrayList<>();
    long current = 0L;
    int length = encodedProductIndices.length();
    int index = 0;

    while (index < length) {
      while (index < length && Character.isWhitespace(encodedProductIndices.charAt(index))) {
        index += 1;
      }
      if (index >= length) {
        break;
      }

      long delta = 0L;
      boolean sawDigit = false;
      while (index < length && Character.isDigit(encodedProductIndices.charAt(index))) {
        sawDigit = true;
        delta = Math.addExact(Math.multiplyExact(delta, 10L), encodedProductIndices.charAt(index) - '0');
        index += 1;
      }

      if (!sawDigit) {
        throw new IllegalArgumentException("Invalid delta posting list near character " + index);
      }

      current = Math.addExact(current, delta);
      result.add(current);
    }

    return result;
  }
}
