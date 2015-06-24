package org.segrada.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class OrientStringEscapeTest {

	@Test
	public void testEscapeOrientSql() throws Exception {
		assertNull(OrientStringEscape.escapeOrientSql(null));
		assertEquals("", OrientStringEscape.escapeOrientSql(""));
		assertEquals("test", OrientStringEscape.escapeOrientSql("test"));
		assertEquals("\"test\"", OrientStringEscape.escapeOrientSql("\"test\""));
		assertEquals("\\'test\\'", OrientStringEscape.escapeOrientSql("'test'"));
	}
}