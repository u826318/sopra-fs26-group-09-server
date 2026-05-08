package ch.uzh.ifi.hase.soprafs26.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class ReceiptAbbreviationService {

  private static final Map<String, String> PHRASE_ABBREVIATIONS = Map.ofEntries(
      Map.entry("apl jc", "apple juice"),
      Map.entry("bread wht", "wheat bread"),
      Map.entry("blk tea", "black tea"),
      Map.entry("chk brst", "chicken breast"),
      Map.entry("choc bar", "chocolate bar"),
      Map.entry("chs chdr", "cheese cheddar"),
      Map.entry("grk yog", "greek yogurt"),
      Map.entry("grn tea", "green tea"),
      Map.entry("hrbl tea", "herbal tea"),
      Map.entry("ice tea", "iced tea"),
      Map.entry("mlk drk", "milk drink"),
      Map.entry("mlk tea", "milk tea"),
      Map.entry("oliv oil", "olive oil"),
      Map.entry("psta spgh", "pasta spaghetti"),
      Map.entry("strw yog", "strawberry yogurt"),
      Map.entry("tmto sauce", "tomato sauce"),
      Map.entry("whl wht", "whole wheat"),
      Map.entry("ww brd", "whole wheat bread"),
      Map.entry("yog strw", "yogurt strawberry")
  );

  private static final Map<String, String> ABBREVIATIONS = Map.ofEntries(
      Map.entry("almnd", "almond"),
      Map.entry("apl", "apple"),
      Map.entry("appl", "apple"),
      Map.entry("avo", "avocado"),
      Map.entry("avoc", "avocado"),
      Map.entry("baby", "baby"),
      Map.entry("bacn", "bacon"),
      Map.entry("bana", "banana"),
      Map.entry("ban", "banana"),
      Map.entry("bbq", "barbecue"),
      Map.entry("bf", "beef"),
      Map.entry("bio", "organic"),
      Map.entry("blk", "black"),
      Map.entry("blnd", "blend"),
      Map.entry("bnls", "boneless"),
      Map.entry("brst", "breast"),
      Map.entry("brd", "bread"),
      Map.entry("brkfst", "breakfast"),
      Map.entry("broc", "broccoli"),
      Map.entry("brwn", "brown"),
      Map.entry("btr", "butter"),
      Map.entry("cab", "cabbage"),
      Map.entry("carb", "carbonated"),
      Map.entry("carrot", "carrot"),
      Map.entry("carrots", "carrot"),
      Map.entry("cereal", "cereal"),
      Map.entry("ched", "cheddar"),
      Map.entry("chdr", "cheddar"),
      Map.entry("cheez", "cheese"),
      Map.entry("chs", "cheese"),
      Map.entry("chkn", "chicken"),
      Map.entry("chk", "chicken"),
      Map.entry("choc", "chocolate"),
      Map.entry("cn", "can"),
      Map.entry("cnd", "canned"),
      Map.entry("cocnt", "coconut"),
      Map.entry("cond", "condensed"),
      Map.entry("crm", "cream"),
      Map.entry("crmy", "creamy"),
      Map.entry("ctg", "cottage"),
      Map.entry("dk", "dark"),
      Map.entry("drk", "drink"),
      Map.entry("drsg", "dressing"),
      Map.entry("eg", "egg"),
      Map.entry("eggs", "egg"),
      Map.entry("egg", "egg"),
      Map.entry("evop", "evaporated"),
      Map.entry("frsh", "fresh"),
      Map.entry("frz", "frozen"),
      Map.entry("gar", "garlic"),
      Map.entry("grlc", "garlic"),
      Map.entry("gran", "granola"),
      Map.entry("grk", "greek"),
      Map.entry("grn", "green"),
      Map.entry("grnd", "ground"),
      Map.entry("hambrg", "hamburger"),
      Map.entry("hvy", "heavy"),
      Map.entry("hrbl", "herbal"),
      Map.entry("ital", "italian"),
      Map.entry("jce", "juice"),
      Map.entry("juic", "juice"),
      Map.entry("kchn", "kitchen"),
      Map.entry("lf", "low fat"),
      Map.entry("lg", "large"),
      Map.entry("lt", "light"),
      Map.entry("m", "medium"),
      Map.entry("med", "medium"),
      Map.entry("mac", "macaroni"),
      Map.entry("mayo", "mayonnaise"),
      Map.entry("mincd", "minced"),
      Map.entry("mlk", "milk"),
      Map.entry("mozz", "mozzarella"),
      Map.entry("mshrm", "mushroom"),
      Map.entry("mush", "mushroom"),
      Map.entry("nat", "natural"),
      Map.entry("oliv", "olive"),
      Map.entry("orng", "orange"),
      Map.entry("org", "organic"),
      Map.entry("parmsn", "parmesan"),
      Map.entry("pb", "peanut butter"),
      Map.entry("pnut", "peanut"),
      Map.entry("pot", "potato"),
      Map.entry("psta", "pasta"),
      Map.entry("ptto", "potato"),
      Map.entry("rd", "red"),
      Map.entry("rf", "reduced fat"),
      Map.entry("ric", "rice"),
      Map.entry("rom", "romaine"),
      Map.entry("rotis", "rotisserie"),
      Map.entry("salm", "salmon"),
      Map.entry("saus", "sausage"),
      Map.entry("shrd", "shredded"),
      Map.entry("sknls", "skinless"),
      Map.entry("slcd", "sliced"),
      Map.entry("sm", "small"),
      Map.entry("snack", "snack"),
      Map.entry("sourcrm", "sour cream"),
      Map.entry("spcy", "spicy"),
      Map.entry("spgh", "spaghetti"),
      Map.entry("spin", "spinach"),
      Map.entry("straw", "strawberry"),
      Map.entry("strw", "strawberry"),
      Map.entry("sw", "sweet"),
      Map.entry("tom", "tomato"),
      Map.entry("tmto", "tomato"),
      Map.entry("tort", "tortilla"),
      Map.entry("trky", "turkey"),
      Map.entry("van", "vanilla"),
      Map.entry("veg", "vegetable"),
      Map.entry("whl", "whole"),
      Map.entry("wht", "wheat"),
      Map.entry("wtr", "water"),
      Map.entry("ww", "whole wheat"),
      Map.entry("yel", "yellow"),
      Map.entry("yog", "yogurt")
  );

  private static final Set<String> AMBIGUOUS_TOKENS = Set.of(
      "bar", "lt", "pch", "roll", "wht"
  );

  private static final Set<String> LOW_VALUE_TOKENS = Set.of(
      "amt", "bag", "btl", "bulk", "can", "cnt", "ct", "cup", "disc", "ea", "each",
      "fl", "g", "kg", "lb", "lbs", "liter", "litre", "ltr", "ml", "oz", "pcs", "pc",
      "pk", "pkg", "reg", "sale", "save",
      "subtotal", "tax", "total", "unit", "void", "x"
  );

  public String normalize(String description) {
    String phraseExpanded = expandPhrases(description);
    List<String> tokens = tokenize(phraseExpanded);
    List<String> normalizedTokens = new ArrayList<>();
    for (String token : tokens) {
      if (isLowValueToken(token)) {
        continue;
      }
      normalizedTokens.add(normalizeToken(token));
    }
    return String.join(" ", normalizedTokens);
  }

  private String expandPhrases(String description) {
    String normalizedText = normalizeSeparators(description);
    if (normalizedText == null) {
      return "";
    }

    List<Map.Entry<String, String>> phrases = PHRASE_ABBREVIATIONS.entrySet().stream()
        .sorted(Comparator.comparingInt((Map.Entry<String, String> entry) -> entry.getKey().length()).reversed())
        .toList();

    String expanded = normalizedText;
    for (Map.Entry<String, String> phrase : phrases) {
      expanded = expanded.replace(phrase.getKey(), phrase.getValue());
    }
    return expanded;
  }

  private String normalizeToken(String token) {
    if (AMBIGUOUS_TOKENS.contains(token)) {
      return token;
    }
    return ABBREVIATIONS.getOrDefault(token, token);
  }

  public List<String> tokenize(String value) {
    String trimmed = blankToNull(value);
    if (trimmed == null) {
      return List.of();
    }

    List<String> tokens = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    for (int i = 0; i < trimmed.length(); i++) {
      char character = Character.toLowerCase(trimmed.charAt(i));
      if (Character.isLetterOrDigit(character)) {
        current.append(character);
      }
      else if (current.length() > 0) {
        tokens.add(current.toString());
        current.setLength(0);
      }
    }
    if (current.length() > 0) {
      tokens.add(current.toString());
    }
    return tokens;
  }

  public boolean isLowValueToken(String token) {
    return token == null || LOW_VALUE_TOKENS.contains(token) || isMostlyNumeric(token);
  }

  private boolean isMostlyNumeric(String token) {
    if (hasLeadingDigitsThenLetters(token) || hasLeadingLettersThenDigits(token)) {
      return true;
    }
    int digitCount = 0;
    for (int i = 0; i < token.length(); i++) {
      if (Character.isDigit(token.charAt(i))) {
        digitCount++;
      }
    }
    return digitCount > 0 && digitCount >= token.length() - digitCount;
  }

  private String blankToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String normalizeSeparators(String value) {
    String trimmed = blankToNull(value);
    if (trimmed == null) {
      return null;
    }

    StringBuilder normalized = new StringBuilder(trimmed.length());
    for (int i = 0; i < trimmed.length(); i++) {
      char character = Character.toLowerCase(trimmed.charAt(i));
      normalized.append(Character.isLetterOrDigit(character) ? character : ' ');
    }
    return collapseWhitespace(normalized);
  }

  private boolean hasLeadingDigitsThenLetters(String token) {
    int index = 0;
    while (index < token.length() && Character.isDigit(token.charAt(index))) {
      index++;
    }
    if (index == 0 || index == token.length()) {
      return false;
    }
    while (index < token.length()) {
      if (!Character.isLetter(token.charAt(index))) {
        return false;
      }
      index++;
    }
    return true;
  }

  private boolean hasLeadingLettersThenDigits(String token) {
    int index = 0;
    while (index < token.length() && Character.isLetter(token.charAt(index))) {
      index++;
    }
    if (index == 0 || index == token.length()) {
      return false;
    }
    while (index < token.length()) {
      if (!Character.isDigit(token.charAt(index))) {
        return false;
      }
      index++;
    }
    return true;
  }

  private String collapseWhitespace(CharSequence value) {
    StringBuilder collapsed = new StringBuilder(value.length());
    boolean previousWasWhitespace = true;
    for (int i = 0; i < value.length(); i++) {
      char character = value.charAt(i);
      if (Character.isWhitespace(character)) {
        if (!previousWasWhitespace) {
          collapsed.append(' ');
          previousWasWhitespace = true;
        }
      }
      else {
        collapsed.append(character);
        previousWasWhitespace = false;
      }
    }

    int length = collapsed.length();
    if (length > 0 && collapsed.charAt(length - 1) == ' ') {
      collapsed.setLength(length - 1);
    }
    return collapsed.toString();
  }
}
