
package com.idraak.quantum

/**
 * IDRAAK - Command Execution Manager
 *
 * Responsibility:
 * Coordinates execution of parsed phone commands
 * through the appropriate IDRAAK controllers.
 *
 * PhoneCommandParser is responsible for parsing.
 * VoiceCommandRouter is responsible for routing.
 *
 * This class does not recognize speech, parse raw text,
 * communicate with Gemini, or manage voice sessions.
 *
 * Controller-specific execution will be integrated
 * after their actual interfaces are reviewed.
 */
internal class CommandExecutionManager(
    private val softwareController: SoftwareController,
    private val systemController: SystemController,
    private val screenController: ScreenController,
    private val hardwareController: HardwareController
)
