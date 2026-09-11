package mekanism.common.lib;

import java.util.Locale;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Test WildcardMatcher implementation")
class WildcardMatcherTest {

    @Test
    @DisplayName("Test universal wildcard")
    void testUniversalWildcard() {
        Assertions.assertTrue(WildcardMatcher.matches("*", "test"));
        Assertions.assertTrue(WildcardMatcher.matches("*", ""));
    }

    @Test
    @DisplayName("Test exact string matching")
    void testExactMatch() {
        Assertions.assertTrue(WildcardMatcher.matches("", ""));
        Assertions.assertTrue(WildcardMatcher.matches("hello", "hello"));
        Assertions.assertFalse(WildcardMatcher.matches("hell", "hello"));
        Assertions.assertFalse(WildcardMatcher.matches("hello", "hell"));
        Assertions.assertFalse(WildcardMatcher.matches("", "hello"));
        Assertions.assertFalse(WildcardMatcher.matches("hello", ""));
    }

    @Test
    @DisplayName("Test character-specific wildcards")
    void testCharacterSpecificWildcards() {
        Assertions.assertTrue(WildcardMatcher.matches("he?lo", "hello"));
        Assertions.assertTrue(WildcardMatcher.matches("#23#", "1234"));
        Assertions.assertTrue(WildcardMatcher.matches("?###?", "h329o"));
        Assertions.assertFalse(WildcardMatcher.matches("??#?", "AAAA"));
        Assertions.assertFalse(WildcardMatcher.matches("?????", "AAAA"));
    }

    @Test
    @DisplayName("Test wildcard character")
    void testWildcardGroupings() {
        Assertions.assertTrue(WildcardMatcher.matches("*******", "test"));
        Assertions.assertTrue(WildcardMatcher.matches("*test*", "12341234test23523"));
        Assertions.assertTrue(WildcardMatcher.matches("test*", "test23423424"));
        Assertions.assertTrue(WildcardMatcher.matches("*test", "234234test"));
        Assertions.assertTrue(WildcardMatcher.matches("*test*another*", "23234test2342389another23423"));
        Assertions.assertTrue(WildcardMatcher.matches("*#*", "asudnfai3asifa"));
        Assertions.assertFalse(WildcardMatcher.matches("*#*", "asudnfaiasifa"));
        Assertions.assertFalse(WildcardMatcher.matches("*test*tester", "29342398test289289tester3"));
    }

    @Test
    @DisplayName("Case folding is independent of the default locale")
    void testRootLocaleCaseInsensitivity() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            Assertions.assertTrue(WildcardMatcher.matches("I", "i"));
            Assertions.assertTrue(WildcardMatcher.matches("*I*", "prefix-i-suffix"));
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("Tag keys match their namespace and path")
    void testTagKeyMatching() {
        TagKey<?> tag = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("mekanism", "storage/alloys"));
        Assertions.assertTrue(WildcardMatcher.matches("MEK*:*ALLOYS", tag));
        Assertions.assertFalse(WildcardMatcher.matches("minecraft:*", tag));
    }

    @Test
    @DisplayName("Numeric wildcard uses UTF-16 char digit semantics")
    void testDigitWildcardCharSemantics() {
        Assertions.assertTrue(Character.isDigit('\u0665'));
        Assertions.assertTrue(WildcardMatcher.matches("value-#", "value-\u0665"));
        Assertions.assertFalse(WildcardMatcher.matches("value-#", "value-A"));

        String supplementaryDigit = "\uD835\uDFCE"; // U+1D7CE MATHEMATICAL BOLD DIGIT ZERO
        Assertions.assertTrue(Character.isDigit(supplementaryDigit.codePointAt(0)));
        Assertions.assertFalse(Character.isDigit(supplementaryDigit.charAt(0)));
        Assertions.assertFalse(Character.isDigit(supplementaryDigit.charAt(1)));
        Assertions.assertFalse(WildcardMatcher.matches("#", supplementaryDigit));
    }
}
