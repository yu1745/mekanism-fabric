package mekanism.common.base.holiday;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.Month;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class HolidayValueTest {

    @Test
    void yearlyDateMatchesExactMonthAndDayIncludingLeapDayValues() {
        YearlyDate leapDay = new YearlyDate(Month.FEBRUARY, 29);

        assertTrue(leapDay.isToday(new YearlyDate(Month.FEBRUARY, 29)));
        assertFalse(leapDay.isToday(new YearlyDate(Month.FEBRUARY, 28)));
        assertFalse(leapDay.isToday(new YearlyDate(Month.MARCH, 29)));
    }

    @Test
    void monthlyDateMatchesEveryDayOnlyWithinItsMonth() {
        MonthlyDate january = new MonthlyDate(Month.JANUARY);

        assertTrue(january.isToday(new YearlyDate(Month.JANUARY, 1)));
        assertTrue(january.isToday(new YearlyDate(Month.JANUARY, 31)));
        assertFalse(january.isToday(new YearlyDate(Month.FEBRUARY, 1)));
    }

    @Test
    void yearlyDateNowMatchesEitherLocalDateSnapshotAcrossMidnight() {
        LocalDate before = LocalDate.now();
        YearlyDate actual = YearlyDate.now();
        LocalDate after = LocalDate.now();
        YearlyDate beforeSnapshot = new YearlyDate(before.getMonth(), before.getDayOfMonth());
        YearlyDate afterSnapshot = new YearlyDate(after.getMonth(), after.getDayOfMonth());

        assertTrue(actual.equals(beforeSnapshot) || actual.equals(afterSnapshot));
    }

    @Test
    void holidayMessageEqualityUsesThemedValueAndOrderedDeepLineContent() {
        Component[] originalLines = {Component.literal("first"), Component.literal("second")};
        Component[] equivalentLines = {Component.literal("first"), Component.literal("second")};
        HolidayMessage original = new HolidayMessage(Component.literal("theme"), originalLines);
        HolidayMessage equivalent = new HolidayMessage(Component.literal("theme"), equivalentLines);

        assertNotSame(originalLines, equivalentLines);
        assertEquals(original, equivalent);
        assertEquals(original.hashCode(), equivalent.hashCode());
        assertNotEquals(original,
              new HolidayMessage(Component.literal("theme"), Component.literal("second"), Component.literal("first")));
        assertNotEquals(original,
              new HolidayMessage(Component.literal("other-theme"), Component.literal("first"), Component.literal("second")));
        assertNotEquals(original,
              new HolidayMessage(Component.literal("theme"), Component.literal("first"), Component.literal("other-line")));
        assertNotEquals(original, null);
    }

    @Test
    void holidayMessageAccessorsRetainReferencesOrderAndCallerArrayAliasing() {
        Component themed = Component.literal("theme");
        Component first = Component.literal("first");
        Component second = Component.literal("second");
        Component[] lines = {first, second};
        HolidayMessage message = new HolidayMessage(themed, lines);

        assertSame(themed, message.themedLines());
        assertSame(lines, message.lines());
        assertArrayEquals(new Component[]{first, second}, message.lines());

        Component replacement = Component.literal("replacement");
        lines[0] = replacement;
        assertSame(replacement, message.lines()[0]);
        assertSame(second, message.lines()[1]);
    }
}
