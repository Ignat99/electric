package com.sun.electric.api.minarea.launcher;

import java.lang.management.ManagementFactory;

import org.junit.Test;

public class TestGetPID {
	
	@Test
	public void testGetPID() {
		System.out.println(ManagementFactory.getRuntimeMXBean().getName());
		String name = ManagementFactory.getRuntimeMXBean().getName();
		System.out.println(name.split("@")[0]);
	}

}
