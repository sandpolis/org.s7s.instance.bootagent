//============================================================================//
//                                                                            //
//                         Copyright © 2015 Sandpolis                         //
//                                                                            //
//  This source file is subject to the terms of the Mozilla Public License    //
//  version 2. You may not use this file except in compliance with the MPL    //
//  as published by the Mozilla Foundation.                                   //
//                                                                            //
//============================================================================//

plugins {
	id("sandpolis-instance")
	id("sandpolis-publish")
}

dependencies {
	proto("com.sandpolis:core.foundation:+:rust@zip")
	proto("com.sandpolis:core.instance:+:rust@zip")
	proto("com.sandpolis:core.net:+:rust@zip")
	proto("com.sandpolis:plugin.snapshot:+:rust@zip")
}

val buildAmd64 by tasks.creating(Exec::class) {
	dependsOn("assembleProto")
	workingDir(project.getProjectDir())
	commandLine(listOf("cargo", "+nightly", "build", "--release", "--target=x86_64-unknown-uefi"))
	outputs.files("target/x86_64-unknown-uefi/release/agent.efi")
}

val buildAarch64 by tasks.creating(Exec::class) {
	dependsOn("assembleProto")
	workingDir(project.getProjectDir())
	commandLine(listOf("cargo", "+nightly", "build", "--release", "--target=aarch64-unknown-uefi"))
	outputs.files("target/aarch64-unknown-uefi/release/agent.efi")
}

tasks.findByName("build")?.dependsOn(buildAmd64, buildAarch64)

tasks.findByName("clean")?.doLast {
	delete("src/gen")
}

val runAmd64 by tasks.creating(Exec::class) {
	dependsOn(buildAmd64)
	workingDir(project.getProjectDir())
	commandLine(listOf(
		"qemu-system-x86_64",

		// Setup system
		"-nodefaults", "--enable-kvm", "-m", "256M", "-machine", "q35", "-smp", "4",

		// UEFI firmware stuff
		"-drive", "if=pflash,format=raw,file=/usr/share/ovmf/x64/OVMF_CODE.fd,readonly=on", "-drive", "if=pflash,format=raw,file=/usr/share/ovmf/x64/OVMF_VARS.fd,readonly=on",

		// Mount build directory
		"-drive", "format=raw,file=fat:rw:${project.getProjectDir()}/build/esp",

		// Setup NIC
		"-netdev", "user,id=user.0", "-device", "rtl8139,netdev=user.0", // "-object", "filter-dump,id=id,netdev=user.0,file=/tmp/test.pcap",

		// Setup STDOUT
		"-serial", "stdio", "-device", "isa-debug-exit,iobase=0xf4,iosize=0x04", "-vga", "std"
	))

	doFirst {
		copy {
			from(buildAarch64.outputs.files.getSingleFile())
			into("${project.getProjectDir()}/build/esp/EFI/Boot/BootX64.efi")
		}
	}
}

val runAarch64 by tasks.creating(Exec::class) {
	dependsOn(buildAarch64)
	workingDir(project.getProjectDir())
	commandLine(listOf(
		"qemu-system-aarch64",

		// Setup system
		"-nodefaults", "--enable-kvm", "-m", "256M", "-machine", "virt", "-cpu", "cortex-a72", "-smp", "4",

		// UEFI firmware stuff
		"-drive", "if=pflash,format=raw,file=/usr/share/ovmf/x64/OVMF_CODE.fd,readonly=on", "-drive", "if=pflash,format=raw,file=/usr/share/ovmf/x64/OVMF_VARS.fd,readonly=on",

		// Mount build directory
		"-drive", "format=raw,file=fat:rw:${project.getProjectDir()}/build/esp",

		// Setup NIC
		"-netdev", "user,id=user.0", "-device", "rtl8139,netdev=user.0", // "-object", "filter-dump,id=id,netdev=user.0,file=/tmp/test.pcap",

		// Setup STDOUT
		"-serial", "stdio", "-device", "isa-debug-exit,iobase=0xf4,iosize=0x04", "-vga", "std"
	))

	doFirst {
		copy {
			from(buildAarch64.outputs.files.getSingleFile())
			into("${project.getProjectDir()}/build/esp/EFI/Boot/BootAA64.efi")
		}
	}
}

publishing {
	publications {
		create<MavenPublication>("agent") {
			groupId = "com.sandpolis"
			artifactId = "agent.boot"
			version = project.version.toString()

			artifact(buildAmd64.outputs.files.filter { it.name == "agent" }.getSingleFile()) {
				classifier = "amd64"
			}

			artifact(buildAarch64.outputs.files.filter { it.name == "agent" }.getSingleFile()) {
				classifier = "aarch64"
			}
		}
		tasks.findByName("publishAgentPublicationToCentralStagingRepository")?.dependsOn("build")
	}
}
