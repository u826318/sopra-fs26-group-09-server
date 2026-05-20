package ch.uzh.ifi.hase.soprafs26.service.localdatasetlookup;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

@Component
public class NameSearchTextNormalizer {

  public List<String> tokenize(String raw) {
    String normalized = normalizeText(raw);
    if (normalized.isBlank()) {
      return Collections.emptyList();
    }

    List<String> tokens = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (int i = 0; i < normalized.length(); i += 1) {
      char ch = normalized.charAt(i);
      if (Character.isLetterOrDigit(ch)) {
        current.append(ch);
      }
      else if (current.length() > 0) {
        tokens.add(current.toString());
        current.setLength(0);
      }
    }
    if (current.length() > 0) {
      tokens.add(current.toString());
    }
    return tokens.stream().filter(token -> token.length() >= 2).distinct().toList();
  }

  public String normalizeText(String raw) {
    if (raw == null) {
      return "";
    }
    String decomposed = Normalizer.normalize(raw, Normalizer.Form.NFD).toLowerCase(Locale.ROOT);
    StringBuilder builder = new StringBuilder(decomposed.length());
    boolean lastWasSpace = false;
    for (int i = 0; i < decomposed.length(); i += 1) {
      char ch = decomposed.charAt(i);
      int type = Character.getType(ch);
      if (type == Character.NON_SPACING_MARK
          || type == Character.COMBINING_SPACING_MARK
          || type == Character.ENCLOSING_MARK) {
        continue;
      }
      if (Character.isLetterOrDigit(ch)) {
        builder.append(ch);
        lastWasSpace = false;
      }
      else if (!lastWasSpace) {
        builder.append(' ');
        lastWasSpace = true;
      }
    }
    return builder.toString().trim();
  }

  public String compactText(String raw) {
    String normalized = normalizeText(raw);
    StringBuilder compacted = new StringBuilder(normalized.length());
    for (int i = 0; i < normalized.length(); i += 1) {
      char ch = normalized.charAt(i);
      if (Character.isLetterOrDigit(ch)) {
        compacted.append(ch);
      }
    }
    return compacted.toString();
  }
}
