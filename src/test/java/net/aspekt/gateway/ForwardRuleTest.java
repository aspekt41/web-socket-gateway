package net.aspekt.gateway;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ForwardRuleTest {

    @Test
    void fromAccessor_returnsFrom() {
        ForwardRule rule = new ForwardRule("source", "dest");
        assertEquals("source", rule.from());
    }

    @Test
    void toAccessor_returnsTo() {
        ForwardRule rule = new ForwardRule("source", "dest");
        assertEquals("dest", rule.to());
    }

    @Test
    void equalRulesAreEqual() {
        ForwardRule a = new ForwardRule("x", "y");
        ForwardRule b = new ForwardRule("x", "y");
        assertEquals(a, b);
    }

    @Test
    void differentFromProducesInequality() {
        assertNotEquals(new ForwardRule("a", "z"), new ForwardRule("b", "z"));
    }

    @Test
    void differentToProducesInequality() {
        assertNotEquals(new ForwardRule("x", "a"), new ForwardRule("x", "b"));
    }

    @Test
    void equalRulesHaveSameHashCode() {
        assertEquals(new ForwardRule("p", "q").hashCode(), new ForwardRule("p", "q").hashCode());
    }

    @Test
    void toStringContainsFromAndTo() {
        String s = new ForwardRule("alpha", "beta").toString();
        assertTrue(s.contains("alpha"));
        assertTrue(s.contains("beta"));
    }
}
