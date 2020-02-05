package com.github.pfichtner.bytebuddy.pg._2_own_anno_with_agent;

import static net.bytebuddy.agent.ByteBuddyAgent.install;
import static org.junit.jupiter.api.Assertions.assertThrows;

import javax.validation.ConstraintViolationException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TestPerson {

	@BeforeAll
	public static void setUp() throws Exception {
		Agent.premain(null, install());
	}

	@Test
	void assertThrowsException() {
		assertThrows(ConstraintViolationException.class, () -> new Person("x"));
	}

	@Test
	void assertOk() {
		new Person("xx");
	}

}
