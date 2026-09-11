package mekanism.common.util;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Java 21 behavior coverage for the unchanged official {@link LambdaMetaFactoryUtil}.
 */
class LambdaMetaFactoryUtilTest {

    private static final List<ValueCase> DIRECT_CASES = List.of(
          new ValueCase("booleanValue", true, false, true),
          new ValueCase("byteValue", (byte) 1, (byte) 12, (byte) -7),
          new ValueCase("charValue", 'a', '\u03bb', '\0'),
          new ValueCase("doubleValue", 1.25D, -2.5D, Double.POSITIVE_INFINITY),
          new ValueCase("floatValue", 2.5F, -3.75F, Float.NaN),
          new ValueCase("intValue", 3, -42, Integer.MAX_VALUE),
          new ValueCase("longValue", 4L, 9_876_543_210L, Long.MIN_VALUE),
          new ValueCase("shortValue", (short) 5, (short) -123, Short.MAX_VALUE),
          new ValueCase("referenceValue", "initial", "updated", null)
    );

    private static final List<NamedCase> NAMED_CASES = List.of(
          new NamedCase("booleanValue", "getBooleanValue", "setBooleanValue", true, true, false, false, true),
          new NamedCase("byteValue", "getByteValue", "setByteValue", (byte) 1, (byte) 10, (byte) 11, (byte) -3, (byte) -2),
          new NamedCase("charValue", "getCharValue", "setCharValue", 'a', 'k', 'l', '\u03bb', '\u03bc'),
          new NamedCase("doubleValue", "getDoubleValue", "setDoubleValue", 1.25D, 10D, 10.5D, -4D, -3.5D),
          new NamedCase("floatValue", "getFloatValue", "setFloatValue", 2.5F, 8F, 8.25F, -2F, -1.75F),
          new NamedCase("intValue", "getIntValue", "setIntValue", 3, 7, 17, -20, -10),
          new NamedCase("longValue", "getLongValue", "setLongValue", 4L, 50L, 150L, -300L, -200L),
          new NamedCase("shortValue", "getShortValue", "setShortValue", (short) 5, (short) 20, (short) 22, (short) -9, (short) -7),
          new NamedCase("referenceValue", "getReferenceValue", "setReferenceValue", "initial", "value", "named:value", null, "named:null")
    );

    @Test
    void accessibleDirectFieldsRoundTripEveryPrimitiveAndReferenceIndependently() throws Throwable {
        for (ValueCase valueCase : DIRECT_CASES) {
            Field field = DirectFixture.class.getDeclaredField(valueCase.fieldName());
            field.setAccessible(true);
            Function<DirectFixture, Object> getter = LambdaMetaFactoryUtil.createGetter(field, DirectFixture.class, "");
            BiConsumer<DirectFixture, Object> setter = LambdaMetaFactoryUtil.createSetter(field, DirectFixture.class, "");
            DirectFixture first = new DirectFixture();
            DirectFixture second = new DirectFixture();

            assertEquals(valueCase.initial(), getter.apply(first), valueCase.fieldName());
            assertEquals(valueCase.initial(), getter.apply(second), valueCase.fieldName());
            if (field.getType().isPrimitive()) {
                assertInstanceOf(boxedType(field.getType()), getter.apply(first), valueCase.fieldName());
            }

            setter.accept(first, valueCase.firstValue());
            assertEquals(valueCase.firstValue(), getter.apply(first), valueCase.fieldName());
            assertEquals(valueCase.initial(), getter.apply(second), valueCase.fieldName());

            setter.accept(second, valueCase.secondValue());
            assertEquals(valueCase.firstValue(), getter.apply(first), valueCase.fieldName());
            assertEquals(valueCase.secondValue(), getter.apply(second), valueCase.fieldName());
            assertEquals(valueCase.secondValue(), getter.apply(second), valueCase.fieldName());
        }
    }

    @Test
    void namedMethodsCoverEveryPrimitiveSetterBranchAndObjectBranch() throws Throwable {
        for (NamedCase namedCase : NAMED_CASES) {
            Field field = NamedFixture.class.getDeclaredField(namedCase.fieldName());
            Function<NamedFixture, Object> getter = LambdaMetaFactoryUtil.createGetter(field, NamedFixture.class, namedCase.getterName());
            BiConsumer<NamedFixture, Object> setter = LambdaMetaFactoryUtil.createSetter(field, NamedFixture.class, namedCase.setterName());
            NamedFixture first = new NamedFixture();
            NamedFixture second = new NamedFixture();

            assertEquals(namedCase.initial(), getter.apply(first), namedCase.fieldName());
            assertEquals(1, first.getterCalls);
            setter.accept(first, namedCase.firstInput());
            assertEquals(1, first.setterCalls);
            assertEquals(namedCase.firstStored(), getter.apply(first), namedCase.fieldName());
            assertEquals(namedCase.firstStored(), getter.apply(first), namedCase.fieldName());
            assertEquals(3, first.getterCalls);

            assertEquals(namedCase.initial(), getter.apply(second), namedCase.fieldName());
            assertEquals(1, second.getterCalls);
            assertEquals(0, second.setterCalls);
            setter.accept(second, namedCase.secondInput());
            assertEquals(1, second.setterCalls);
            assertEquals(namedCase.secondStored(), getter.apply(second), namedCase.fieldName());
            assertEquals(namedCase.firstStored(), getter.apply(first), namedCase.fieldName());
        }
    }

    @Test
    void missingNamedGetterPropagatesLookupFailure() throws NoSuchFieldException {
        Field field = NamedFixture.class.getDeclaredField("intValue");
        assertThrows(NoSuchMethodException.class,
              () -> LambdaMetaFactoryUtil.createGetter(field, NamedFixture.class, "missingGetter"));
    }

    @Test
    void missingNamedSetterPropagatesLookupFailure() throws NoSuchFieldException {
        Field field = NamedFixture.class.getDeclaredField("intValue");
        assertThrows(NoSuchMethodException.class,
              () -> LambdaMetaFactoryUtil.createSetter(field, NamedFixture.class, "missingSetter"));
    }

    @Test
    void mismatchedNamedAccessorSignaturesPropagateLookupFailure() throws NoSuchFieldException {
        Field booleanField = NamedFixture.class.getDeclaredField("booleanValue");
        assertThrows(NoSuchMethodException.class,
              () -> LambdaMetaFactoryUtil.createGetter(booleanField, NamedFixture.class, "getIntValue"));
        assertThrows(NoSuchMethodException.class,
              () -> LambdaMetaFactoryUtil.createSetter(booleanField, NamedFixture.class, "setIntValue"));
    }

    @Test
    void inaccessiblePrivateDirectFieldIsNotSilentlyOpened() throws NoSuchFieldException {
        Field field = DirectFixture.class.getDeclaredField("intValue");
        assertThrows(IllegalAccessException.class,
              () -> LambdaMetaFactoryUtil.createGetter(field, DirectFixture.class, ""));
        assertThrows(IllegalAccessException.class,
              () -> LambdaMetaFactoryUtil.createSetter(field, DirectFixture.class, ""));
    }

    @Test
    void primitiveNamedSetterRejectsNullAndWrongBoxedType() throws Throwable {
        Field field = NamedFixture.class.getDeclaredField("intValue");
        BiConsumer<NamedFixture, Object> setter = LambdaMetaFactoryUtil.createSetter(field, NamedFixture.class, "setIntValue");
        NamedFixture fixture = new NamedFixture();

        assertThrows(NullPointerException.class, () -> setter.accept(fixture, null));
        assertThrows(ClassCastException.class, () -> setter.accept(fixture, "not an int"));
    }

    @Test
    void namedMethodExceptionsPropagateUnchanged() throws Throwable {
        Field field = ThrowingFixture.class.getDeclaredField("value");
        Function<ThrowingFixture, Object> getter = LambdaMetaFactoryUtil.createGetter(field, ThrowingFixture.class, "getThrowing");
        BiConsumer<ThrowingFixture, Object> setter = LambdaMetaFactoryUtil.createSetter(field, ThrowingFixture.class, "setThrowing");
        ThrowingFixture fixture = new ThrowingFixture();

        assertSame(ThrowingFixture.GETTER_FAILURE,
              assertThrows(IllegalStateException.class, () -> getter.apply(fixture)));
        assertSame(ThrowingFixture.SETTER_FAILURE,
              assertThrows(IllegalArgumentException.class, () -> setter.accept(fixture, "ignored")));
    }

    private static Class<?> boxedType(Class<?> primitive) {
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == byte.class) return Byte.class;
        if (primitive == char.class) return Character.class;
        if (primitive == double.class) return Double.class;
        if (primitive == float.class) return Float.class;
        if (primitive == int.class) return Integer.class;
        if (primitive == long.class) return Long.class;
        if (primitive == short.class) return Short.class;
        throw new IllegalArgumentException("Not a covered primitive: " + primitive);
    }

    private record ValueCase(String fieldName, Object initial, Object firstValue, Object secondValue) {
    }

    private record NamedCase(String fieldName, String getterName, String setterName, Object initial,
                             Object firstInput, Object firstStored, Object secondInput, Object secondStored) {
    }

    static final class DirectFixture {

        private boolean booleanValue = true;
        private byte byteValue = 1;
        private char charValue = 'a';
        private double doubleValue = 1.25D;
        private float floatValue = 2.5F;
        private int intValue = 3;
        private long longValue = 4L;
        private short shortValue = 5;
        private String referenceValue = "initial";
    }

    static final class NamedFixture {

        private boolean booleanValue = true;
        private byte byteValue = 1;
        private char charValue = 'a';
        private double doubleValue = 1.25D;
        private float floatValue = 2.5F;
        private int intValue = 3;
        private long longValue = 4L;
        private short shortValue = 5;
        private String referenceValue = "initial";
        private int getterCalls;
        private int setterCalls;

        public boolean getBooleanValue() { getterCalls++; return booleanValue; }
        public void setBooleanValue(boolean value) { setterCalls++; booleanValue = !value; }
        public byte getByteValue() { getterCalls++; return byteValue; }
        public void setByteValue(byte value) { setterCalls++; byteValue = (byte) (value + 1); }
        public char getCharValue() { getterCalls++; return charValue; }
        public void setCharValue(char value) { setterCalls++; charValue = (char) (value + 1); }
        public double getDoubleValue() { getterCalls++; return doubleValue; }
        public void setDoubleValue(double value) { setterCalls++; doubleValue = value + 0.5D; }
        public float getFloatValue() { getterCalls++; return floatValue; }
        public void setFloatValue(float value) { setterCalls++; floatValue = value + 0.25F; }
        public int getIntValue() { getterCalls++; return intValue; }
        public void setIntValue(int value) { setterCalls++; intValue = value + 10; }
        public long getLongValue() { getterCalls++; return longValue; }
        public void setLongValue(long value) { setterCalls++; longValue = value + 100; }
        public short getShortValue() { getterCalls++; return shortValue; }
        public void setShortValue(short value) { setterCalls++; shortValue = (short) (value + 2); }
        public String getReferenceValue() { getterCalls++; return referenceValue; }
        public void setReferenceValue(String value) { setterCalls++; referenceValue = "named:" + value; }
    }

    static final class ThrowingFixture {

        private static final IllegalStateException GETTER_FAILURE = new IllegalStateException("getter sentinel");
        private static final IllegalArgumentException SETTER_FAILURE = new IllegalArgumentException("setter sentinel");
        private String value;

        public String getThrowing() {
            throw GETTER_FAILURE;
        }

        public void setThrowing(String value) {
            throw SETTER_FAILURE;
        }
    }
}
