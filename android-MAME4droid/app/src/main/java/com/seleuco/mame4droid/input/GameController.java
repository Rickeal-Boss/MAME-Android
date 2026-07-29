/*
 * This file is part of MAME4droid.
 *
 * Copyright (C) 2026 David Valdeita (Seleuco)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Linking MAME4droid statically or dynamically with other modules is
 * making a combined work based on MAME4droid. Thus, the terms and
 * conditions of the GNU General Public License cover the whole
 * combination.
 *
 * In addition, as a special exception, the copyright holders of MAME4droid
 * give you permission to combine MAME4droid with free software programs
 * or libraries that are released under the GNU LGPL and with code included
 * in the standard release of MAME under the MAME License (or modified
 * versions of such code, with unchanged license). You may copy and
 * distribute such a system following the terms of the GNU GPL for MAME4droid
 * and the licenses of the other code concerned, provided that you include
 * the source code of that other code when and as the GNU GPL requires
 * distribution of source code.
 *
 * Note that people who make modified versions of MAME4idroid are not
 * obligated to grant this special exception for their modified versions; it
 * is their choice whether to do so. The GNU General Public License
 * gives permission to release a modified version without this exception;
 * this exception also makes it possible to release a modified version
 * which carries forward this exception.
 *
 * MAME4droid is dual-licensed: Alternatively, you can license MAME4droid
 * under a MAME license, as set out in http://mamedev.org/
 */

package com.seleuco.mame4droid.input;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseIntArray;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.content.Context;
import android.hardware.input.InputManager;

import java.util.HashMap;
import android.util.Log;

import com.seleuco.mame4droid.Emulator;
import com.seleuco.mame4droid.MAME4droid;
import com.seleuco.mame4droid.R;
import com.seleuco.mame4droid.helpers.DialogHelper;
import com.seleuco.mame4droid.helpers.MainHelper;
import com.seleuco.mame4droid.helpers.PrefsHelper;
import com.seleuco.mame4droid.widgets.WarnWidget;

import org.json.JSONObject;

import java.util.Arrays;

public class GameController implements IController {

	private static final String TAG = "GameController";
	private static final int FIRST_PERSISTENT_ID = 1000;

	// Global profile fallback (when true, all controllers share ID 9999)
	protected static Boolean fakeID = false;

	// Memory state for dynamic assignment and persistence
	protected static HashMap<Integer, String> genericControllers = new HashMap<>();
	protected static HashMap<String, Integer> persistentIDs = new HashMap<>();
	protected static int nextPersistentID = FIRST_PERSISTENT_ID;

	// Standard MAME arcade inputs
	protected static final int[] emulatorInputValues = {
		UP_VALUE, DOWN_VALUE, LEFT_VALUE, RIGHT_VALUE,
		A_VALUE, B_VALUE, C_VALUE, D_VALUE,
		E_VALUE, F_VALUE, G_VALUE, H_VALUE,
		COIN_VALUE, START_VALUE, EXIT_VALUE, OPTION_VALUE
	};

	// Factory default profile. We use Device ID 0 to keep it isolated from user customizations.
	public static int[] defaultKeyMapping = {
		makeKeyCodeWithDeviceID(0,KeyEvent.KEYCODE_DPAD_UP),
		makeKeyCodeWithDeviceID(0,KeyEvent.KEYCODE_DPAD_DOWN),
		makeKeyCodeWithDeviceID(0,KeyEvent.KEYCODE_DPAD_LEFT),
		makeKeyCodeWithDeviceID(0,KeyEvent.KEYCODE_DPAD_RIGHT),
		makeKeyCodeWithDeviceID(0,KeyEvent.KEYCODE_BUTTON_B),
		makeKeyCodeWithDeviceID(0,KeyEvent.KEYCODE_BUTTON_A),
		makeKeyCodeWithDeviceID(0,KeyEvent.KEYCODE_BUTTON_X),
		makeKeyCodeWithDeviceID(0,KeyEvent.KEYCODE_BUTTON_Y),
		makeKeyCodeWithDeviceID(0,KeyEvent.KEYCODE_BUTTON_L1),
		makeKeyCodeWithDeviceID(0,KeyEvent.KEYCODE_BUTTON_R1),
		makeKeyCodeWithDeviceID(0,KeyEvent.KEYCODE_BUTTON_L2),
		makeKeyCodeWithDeviceID(0,KeyEvent.KEYCODE_BUTTON_R2),
		makeKeyCodeWithDeviceID(0,KeyEvent.KEYCODE_BUTTON_THUMBR),
		makeKeyCodeWithDeviceID(0,KeyEvent.KEYCODE_BUTTON_THUMBL),
		makeKeyCodeWithDeviceID(0, KeyEvent.KEYCODE_BACK),
		makeKeyCodeWithDeviceID(0, KeyEvent.KEYCODE_MENU),
		// Empty padding for remaining slots...
		-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
		-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
		-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
	};

	public static int[] keyMapping = new int[emulatorInputValues.length * 4];

	static protected int MAX_DEVICES = 4;
	static protected int MAX_KEYS = 250;
	protected float MY_PI = 3.14159265f;

	protected int[] oldinput = new int[MAX_DEVICES], newinput = new int[MAX_DEVICES];

	// Maps Android's volatile hardware ID to a player slot (0 to 3)
	public static int[] deviceIDs = new int[MAX_DEVICES];

	static boolean joystickMotion = false;

	// --- Android TV / gamepad connectivity tracking (used for auto-hiding
	// the on-screen touch controls when a physical controller is present) ---
	// volatile: written from the InputManager listener / detectDevice threads and
	// read from the UI thread (e.g. InputHandler.isHideTouchController). Without
	// volatile the UI thread can cache a stale value and fail to hide/show the
	// touch controls promptly on connect/disconnect.
	protected static volatile boolean gamepadConnected = false;
	protected static volatile boolean xboxConnected = false;

	protected int[][] deviceMappings = new int[MAX_KEYS][MAX_DEVICES];
	protected static SparseIntArray banDev = new SparseIntArray(50);
	static protected MAME4droid mm = null;


	// =========================================================================
	// LIFECYCLE & INITIALIZATION
	// =========================================================================

	public void setMAME4droid(MAME4droid value) {
		mm = value;
		if(mm==null) return;

		loadPeristentsIDs();
		fakeID = mm.getPrefsHelper().isFakeID();

		InputManager inputManager = (InputManager) mm.getSystemService(Context.INPUT_SERVICE);
		if (inputManager != null) {
			// Attach to MainLooper to ensure UI messages are safely dispatched on the main thread
			inputManager.registerInputDeviceListener(new InputManager.InputDeviceListener() {
			@Override
			public void onInputDeviceAdded(int deviceId) {
				final InputDevice dev = InputDevice.getDevice(deviceId);
				refreshGamepadConnected();

				boolean isGamepad = false;
				if (dev != null) {
					int src = dev.getSources();
					isGamepad = (src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
						|| (src & InputDevice.SOURCE_CLASS_JOYSTICK) == InputDevice.SOURCE_CLASS_JOYSTICK;
				}

				if (isGamepad) {
					// Eagerly bind the controller to the next free player slot
					// (P1-P4) the instant it is attached, instead of waiting for the
					// first button press. This is what makes plugging several Xbox /
					// USB gamepads into an Android TV's hub before launching a 2-4
					// player game behave predictably: each pad is announced
					// ("Detected XBox controller as P2") and assigned immediately.
					mm.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							checkAndRegisterDevice(dev);
							mm.getMainHelper().updateMAME4droid();
						}
					});
				}
			}

			@Override
			public void onInputDeviceRemoved(int deviceId) {
				banDev.delete(deviceId);
				genericControllers.remove(deviceId);

				boolean wasGamepad = false;
				for (int i = 0; i < MAX_DEVICES; i++) {
					if (deviceIDs[i] == deviceId) {
						deviceIDs[i] = -1; // Free up the player slot
						joystickMotion = false;
						wasGamepad = true;

						Emulator.setDigitalData(i, 0);
						Emulator.setAnalogData(Emulator.LEFT_STICK_DATA, i, 0.0f, 0.0f);
						Emulator.setAnalogData(Emulator.RIGHT_STICK_DATA, i, 0.0f, 0.0f);
						Emulator.setAnalogData(Emulator.TRIGGER_DATA, i, 0.0f, 0.0f);

						final int playerNum = i + 1;

						mm.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								String msg = mm.getString(R.string.controller_disconnected, playerNum);
								new WarnWidget.WarnWidgetHelper(mm, msg, 3, Color.YELLOW, true);
								mm.getMainHelper().updateMAME4droid();
							}
						});
						break;
					}
				}

				refreshGamepadConnected();

				// If no player slot was freed (e.g. a generic/non-autodetected
				// gamepad or a keyboard/mouse unplugged) we still refresh the
				// touch UI so the on-screen controls re-appear when the last
				// physical pad goes away.
				if (!wasGamepad) {
					mm.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							mm.getMainHelper().updateMAME4droid();
						}
					});
				}
			}
			@Override
			public void onInputDeviceChanged(int deviceId) {}
			}, new android.os.Handler(android.os.Looper.getMainLooper()));
		}
		resetAutodetected();
		refreshGamepadConnected();
	}

	public static void resetAutodetected() {
		Arrays.fill(deviceIDs, -1);
		banDev.clear();
		genericControllers.clear();
		joystickMotion = false;
	}

	public boolean isEnabled() {
		int numDevs = 0;
		for (int i = 0; i < MAX_DEVICES; i++) {
			if (deviceIDs[i] != -1) {
				numDevs++;
			}
		}
		return numDevs != 0 || joystickMotion;
	}

	/**
	 * Returns true while ANY physical gamepad / joystick is attached.
	 * Unlike {@link #isEnabled()} (which only flips on the first input event),
	 * this is updated the moment the device is added/removed via the
	 * InputDeviceListener, so the on-screen controls can hide immediately on
	 * connection — exactly what Android TV + Xbox expects.
	 */
	public static boolean isGamepadConnected() {
		return gamepadConnected;
	}

	/**
	 * True while an Xbox-family controller (Xbox One / Series / Elite / Adaptive /
	 * Wireless Bluetooth) is attached. Used to surface Xbox-specific hints and to
	 * drive the "auto-hide virtual buttons" behaviour.
	 */
	public static boolean isXboxConnected() {
		return xboxConnected;
	}

	/**
	 * Matches the various manufacturer strings Android reports for Xbox pads:
	 * "Xbox Wireless Controller", "Xbox One Controller", "Xbox Bluetooth
	 * Gamepad", "Xbox Elite Wireless Controller", "Xbox Adaptive Controller",
	 * "Microsoft Xbox One Controller", etc.
	 */
	protected static boolean isXboxName(String name) {
		if (name == null) return false;
		String n = name.toLowerCase();
		return n.contains("xbox") || n.contains("x-box");
	}

	/**
	 * Re-scans all input devices and refreshes the {@link #gamepadConnected} /
	 * {@link #xboxConnected} flags. Safe to call from any thread; the caller is
	 * responsible for posting UI updates to the main thread.
	 */
	protected static void refreshGamepadConnected() {
		boolean gp = false;
		boolean xb = false;
		int[] ids = InputDevice.getDeviceIds();
		for (int id : ids) {
			InputDevice d = InputDevice.getDevice(id);
			if (d == null) continue;
			int src = d.getSources();
			boolean isGamepad = (src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
			boolean isJoystick = (src & InputDevice.SOURCE_CLASS_JOYSTICK) == InputDevice.SOURCE_CLASS_JOYSTICK;
			if (isGamepad || isJoystick) {
				gp = true;
				if (isXboxName(d.getName())) xb = true;
			}
		}
		gamepadConnected = gp;
		xboxConnected = xb;
	}


	// =========================================================================
	// HARDWARE IDENTITY & PERSISTENCE
	// =========================================================================

	/**
	 * Generates or retrieves a persistent, deterministic Virtual ID for a controller.
	 * It uses the hardware descriptor hash to ensure the ID survives reboots and disconnects.
	 */
	public static int getPersistentDeviceId(InputDevice idev) {
		if (idev == null) return 0;

		// Master Global Profile ID. Prevents collisions with defaults (0) when fakeID is enabled.
		if (fakeID) return 9999;

		try {
			String descriptor = idev.getDescriptor();
			//Log.d(TAG, "Analyzing device: " + idev.getName() + " (Android ID: " + idev.getId() + ")");
			//Log.d(TAG, "Hardware Descriptor: [" + descriptor + "]");

			// Sanity check: ensure descriptor isn't garbage
			if (descriptor != null && descriptor.length() > 4 && !descriptor.equalsIgnoreCase("unknown")) {

				if (persistentIDs.containsKey(descriptor)) {
					int existingId = persistentIDs.get(descriptor);
					//Log.d(TAG, "-> KNOWN CONTROLLER! Returning Persisted ID: " + existingId);
					return existingId;
				} else {
					// First time seeing this gamepad. Assign a new sequential virtual ID.
					int newId = nextPersistentID++;
					Log.d(TAG, "-> NEW CONTROLLER DETECTED! Generating and saving new persisted ID: " + newId);
					persistentIDs.put(descriptor, newId);
					savePersistentsIDs();
					return newId;
				}
			} else {
				Log.d(TAG, "-> WARNING: Trash or empty descriptor. Aborting persistence.");
			}
		} catch (Exception ignored) {}

		// Fallback for badly implemented gamepads without descriptors
		int iControllerNumber = idev.getControllerNumber();
		Log.d(TAG, "-> Using volatile Android fallback. iControllerNumber: " + iControllerNumber);
		if (iControllerNumber > 0) return iControllerNumber & 0xFFFF;

		// NOTE: the virtual ID is packed into the upper 16 bits of a keycode int
		// (see makeKeyCodeWithDeviceID), so it MUST stay within 0..0xFFFF. Some
		// devices report an Android input-device id far larger than 65535; masking
		// here keeps the packed value consistent with getDeviceIdFromKeyCodeWithDeviceID
		// (which reads the upper 16 bits) and prevents silent routing to the wrong player.
		return idev.getId() & 0xFFFF;
	}

	/**
	 * Public getter used primarily by the mapping UI (KeySelect) to bind keys to the correct Virtual ID.
	 */
	public static int getControllerId(InputDevice idev) {
		if (idev == null) return 0;
		int value = getPersistentDeviceId(idev);
		Log.d(TAG, "Controller id is " + value);
		return value;
	}

	public static void loadPeristentsIDs() {
		if(mm == null) return;
		try {
			SharedPreferences prefs = mm.getSharedPreferences("mame4droid_prefs", Context.MODE_PRIVATE);
			String jsonStr = prefs.getString("persistents_ids", "{}");
			JSONObject json = new JSONObject(jsonStr);
			persistentIDs.clear();

			Log.d(TAG, "=== LOADING PERSISTED CONTROLLERS ===");

			java.util.Iterator<String> keys = json.keys();
			while (keys.hasNext()) {
				String k = keys.next();
				if (k.equals("nextPersistentID")) {
					nextPersistentID = json.getInt(k);
				} else {
					persistentIDs.put(k, json.getInt(k));
				}
				Log.d(TAG, "Loaded -> Descriptor: [" + k + "] = Virtual ID: " + json.getInt(k));
			}
			Log.d(TAG, "Next available ID will be: " + nextPersistentID);
			Log.d(TAG, "=======================================");
		} catch (Exception ignored) {
			Log.e(TAG, "Error while loading persisted controllers.");
		}
	}

	public static void savePersistentsIDs() {
		if (mm == null) return;
		try {
			JSONObject json = new JSONObject();
			Log.d(TAG, "=== SAVING PERSISTED CONTROLLERS ===");
			for (java.util.Map.Entry<String, Integer> entry : persistentIDs.entrySet()) {
				json.put(entry.getKey(), entry.getValue());
				Log.d(TAG, "Saving -> Descriptor: [" + entry.getKey() + "] = Virtual ID: " + entry.getValue());
			}
			json.put("nextPersistentID", nextPersistentID);

			SharedPreferences prefs = mm.getSharedPreferences("mame4droid_prefs", Context.MODE_PRIVATE);
			prefs.edit().putString("persistents_ids", json.toString()).apply();
			Log.d(TAG, "Save completed in SharedPreferences.");
			Log.d(TAG, "====================================");
		} catch (Exception ignored) {
			Log.e(TAG, "Error while saving persisted controllers.");
		}
	}

	public static void clearPersistentsIDs() {
		if (mm == null) return;
		try {
			Log.d(TAG, "=== CLEARING PERSISTED CONTROLLERS ===");

			// Wipe RAM state
			persistentIDs.clear();
			nextPersistentID = FIRST_PERSISTENT_ID;

			Log.d(TAG, "RAM cleared: persistentIDs empty, nextPersistentID reset to " + FIRST_PERSISTENT_ID + ".");

			// Wipe Storage
			SharedPreferences prefs = mm.getSharedPreferences("mame4droid_prefs", Context.MODE_PRIVATE);
			prefs.edit().remove("persistents_ids").apply();

			Log.d(TAG, "Storage cleared: 'persistents_ids' removed from SharedPreferences.");
			Log.d(TAG, "======================================");

		} catch (Exception ignored) {
			Log.e(TAG, "Error while clearing persisted controllers.");
		}
	}

	// =========================================================================
	// BITWISE PACKING (Virtual ID + KeyCode)
	// =========================================================================

	// Packs the device ID (upper 16 bits) and the physical key code (lower 16 bits) into a single integer.
	public static int makeKeyCodeWithDeviceID(InputDevice id, int iKeyCode) {
		int value = 0;
		try {
			value = getControllerId(id);
		} catch (Exception ignored) {}
		return makeKeyCodeWithDeviceID(value, iKeyCode);
	}

	public static int makeKeyCodeWithDeviceID(int iDeviceId, int iKeyCode) {
		// The device id occupies the upper 16 bits; mask it so a caller that passes
		// an id wider than 16 bits cannot shift bits past the int and corrupt the pack.
		int iRet = (iDeviceId & 0xFFFF);
		iRet = iRet << 16;
		iRet |= (iKeyCode & 0xFFFF);
		return iRet;
	}

	public static void getInfoFromKeyCodeWithDeviceID(int iKeyCode, int[] iArrRet) {
		iArrRet[0] = iKeyCode >> 16;
		iArrRet[1] = iKeyCode & 0xFFFF;
	}

	public static int getDeviceIdFromKeyCodeWithDeviceID(int iKeyCode) {
		return iKeyCode >> 16;
	}

	public static int getKeyCodeFromKeyCodeWithDeviceID(int iKeyCode) {
		return iKeyCode & 0xFFFF;
	}


	// =========================================================================
	// DYNAMIC CONTROLLER REGISTRATION (The "Seating" Logic)
	// =========================================================================

	protected int checkAndRegisterDevice(InputDevice device) {
		// Ignore virtual keyboards and injected software events (null or ID -1)
		// to prevent them from stealing physical hardware slots.
		if (device == null || device.getId() == -1) return -1;

		int currentId = device.getId();

		//  Always check first if the user has defined a custom profile
		int virtualId = getPersistentDeviceId(device);
		boolean hasCustomProfile = false;
		for (int mappedVal : keyMapping) {
			if (mappedVal != -1) {
				int devId = getDeviceIdFromKeyCodeWithDeviceID(mappedVal);
				int kCode = getKeyCodeFromKeyCodeWithDeviceID(mappedVal);
				if (devId == virtualId && kCode != 0xFFFF && kCode != 0) {
					hasCustomProfile = true;
					break;
				}
			}
		}

		// If a custom profile exists, force the dynamic bridge routing
		if (hasCustomProfile) {
			// Reclaim the controller from autodetect if necessary
			if (!genericControllers.containsKey(currentId)) {
				registerGenericController(device, true);
			}
			return -1; // -1 forces execution into the dynamic bridge path
		}

		// Already registered as a pure Dynamic/Generic controller?
		if (genericControllers.containsKey(currentId)) return -1;

		// Already registered via Autodetect?
		for (int i = 0; i < MAX_DEVICES; i++) {
			if (deviceIDs[i] == currentId) return i;
		}

		// --- FIRST TIME SIGNAL FROM THIS DEVICE ---

		// Attempt Autodetect ONLY if globally enabled
		boolean attemptLegacy = mm.getPrefsHelper().isContollerAutodetect();
		int dev = -1;

		if (attemptLegacy) {
			dev = detectDevice(device);
		}

		// If not, register dynamically (Also filters out raw volume button presses)
		if (dev == -1) {
			int sources = device.getSources();
			boolean isGamepad = (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
			boolean isJoystick = (sources & InputDevice.SOURCE_CLASS_JOYSTICK) == InputDevice.SOURCE_CLASS_JOYSTICK;

			// Only claim a player slot if it's officially categorized as gaming hardware
			if (isGamepad || isJoystick) {
				registerGenericController(device, false);
			}
		}

		return dev;
	}

	/**
	 * Assigns a generic controller to the first available player slot (0 to 3)
	 * and checks if the user has created a custom mapping profile for it.
	 */
	protected void registerGenericController(InputDevice device, boolean hasCustomProfile) {
		if (device == null) return;
		int currentId = device.getId();

		if (!genericControllers.containsKey(currentId)) {
			int activeSlot = -1;
			for (int i = 0; i < MAX_DEVICES; i++) {
				if (deviceIDs[i] == currentId) { activeSlot = i; break; }
			}
			if (activeSlot == -1) {
				for (int i = 0; i < MAX_DEVICES; i++) {
					if (deviceIDs[i] == -1) { deviceIDs[i] = currentId; activeSlot = i; break; }
				}
			}

			String slotName = (activeSlot != -1) ? "P" + (activeSlot + 1) : mm.getString(R.string.controller_unassigned);
			String text;

			if (hasCustomProfile) {
				text = mm.getString(R.string.controller_detected_custom, slotName);
				new WarnWidget.WarnWidgetHelper(mm, text, 3, Color.GREEN, true);
			} else {
				text = mm.getString(R.string.controller_detected_defaults, slotName);
				new WarnWidget.WarnWidgetHelper(mm, text, 3, Color.YELLOW, true);
			}

			genericControllers.put(currentId, slotName);
			mm.getMainHelper().updateMAME4droid();
		}
	}

	protected int getDevice(InputDevice device, boolean detect) {
		if (!mm.getPrefsHelper().isContollerAutodetect()) return -1;
		if (device == null || device.getId() == -1) return -1;

		for (int i = 0; i < MAX_DEVICES; i++) {
			if (deviceIDs[i] == device.getId())
				return i;
		}

		return detect ? detectDevice(device) : -1;
	}


	// =========================================================================
	// CORE INPUT ROUTING & BRIDGING
	// =========================================================================

	protected void setContollerData(int i, KeyEvent event, int data, int[]digital_data) {
		int action = event.getAction();
		if (action == KeyEvent.ACTION_DOWN)
			digital_data[i] |= data;
		else if (action == KeyEvent.ACTION_UP)
			digital_data[i] &= ~data;
	}

	protected boolean handleControllerKey(int value, KeyEvent event, int []digital_data) {
		int v = emulatorInputValues[value % emulatorInputValues.length];

		if (v == EXIT_VALUE) {
			if (event.getAction() == KeyEvent.ACTION_UP) {
				Emulator.setValue(Emulator.EXIT_GAME, 1);
				try { Thread.sleep(InputHandler.PRESS_WAIT); } catch (InterruptedException ignored) {}
				Emulator.setValue(Emulator.EXIT_GAME, 0);
			}
		} else if (v == OPTION_VALUE ) {
			if (event.getAction() == KeyEvent.ACTION_UP && !Emulator.isInOptions()) {
				Emulator.setInOptions(true);
				mm.showDialog(DialogHelper.DIALOG_OPTIONS);
			}
		} else {
			int i = value / emulatorInputValues.length;
			setContollerData(i, event, v, digital_data);
			mm.getInputHandler().fixTiltCoin();
			Emulator.setDigitalData(i, digital_data[i]);
		}
		return true;
	}

	public boolean handleGameController(int keyCode, KeyEvent event, int[] digital_data) {
		InputDevice device = event.getDevice();

		// --- ANDROID TV REMOTE DIRECTION PAD (dual mode) ---
		// TV remote D-pad events arrive with SOURCE_DPAD / SOURCE_KEYBOARD, NOT the
		// GAMEPAD / JOYSTICK source the main gamepad path expects. Intercept them
		// here (before the gamepad routing) and dispatch per the selected mode:
		//   Auto            -> mouse-pointer simulation in mouse games, else direct keys
		//   Mouse pointer   -> D-pad drives the emulated mouse cursor
		//   Direct keys     -> D-pad navigates the MAME OSD like a gamepad stick
		if (isTvRemoteDpad(keyCode, event)) {
			return handleTvDpad(keyCode, event, digital_data);
		}

		// Maintain visual joystick logic (Protected against null devices)
		int sources = (device != null) ? device.getSources() : 0;
		boolean isGamepad = (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
		boolean isJoystick = (sources & InputDevice.SOURCE_CLASS_JOYSTICK) == InputDevice.SOURCE_CLASS_JOYSTICK;

		if (isGamepad || isJoystick) {
			if (!joystickMotion) {
				joystickMotion = true;
				mm.getMainHelper().updateMAME4droid();
			}
		}

		// Input Gatekeeper: Evaluates hardware and routes to Autodetect or Dynamic Bridge
		int dev = checkAndRegisterDevice(device);
		boolean manageDevice = (dev != -1);

		if (!manageDevice) {
			// --- THE DYNAMIC BRIDGE ---
			int virtualId = getPersistentDeviceId(device);
			int actionIndex = -1;
			boolean hasCustomProfile = false;

			// Query Custom Profile
			for (int i = 0; i < keyMapping.length; i++) {
				int mappedVal = keyMapping[i];
				if (mappedVal != -1) {
					int devId = getDeviceIdFromKeyCodeWithDeviceID(mappedVal);
					int kCode = getKeyCodeFromKeyCodeWithDeviceID(mappedVal);

					if (devId == virtualId && kCode != 0xFFFF && kCode != 0) {
						hasCustomProfile = true;
						if (kCode == keyCode) {
							actionIndex = i % emulatorInputValues.length;
							break;
						}
					}
				}
			}

			// Query Factory Defaults (Only fallback if no custom profile exists)
			if (actionIndex == -1 && !hasCustomProfile) {
				for (int i = 0; i < defaultKeyMapping.length; i++) {
					if (defaultKeyMapping[i] == makeKeyCodeWithDeviceID(0, keyCode)) {
						actionIndex = i % emulatorInputValues.length;
						break;
					}
				}
			}

			// Route the resolved action
			if (actionIndex != -1) {
				int activeSlot = -1;

				if (device == null || device.getId() == -1) {
					// Virtual keyboard or on-screen touch controls.
					// Always route these to Player 1 to ensure playability.
					activeSlot = 0;
				} else {
					// Look up the physical slot assigned to this device
					for (int j = 0; j < MAX_DEVICES; j++) {
						if (deviceIDs[j] == device.getId()) {
							activeSlot = j;
							break;
						}
					}

					// Fallback for Bluetooth PC keyboards (not flagged as gamepads and lacking a slot).
					// Also prevents a 5th plugged-in controller from overflowing the player array.
					if (activeSlot == -1 && !isGamepad && !isJoystick) {
						activeSlot = 0;
					}
				}

				if (activeSlot != -1) {
					int mappedValue = (activeSlot * emulatorInputValues.length) + actionIndex;
					if (handleControllerKey(mappedValue, event, digital_data)) return true;
				}
			}

			// --- HARDCODED ANDROID FALLBACKS ---
			if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
				handleControllerKey(14, event, digital_data);
				return true;
			}
			if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
				handleControllerKey(15, event, digital_data);
				return true;
			}
			if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_MODE) {
				// Xbox "Guide" / center button (and similar). The Android TV system
				// may intercept it for the launcher, but when delivered we map it to
				// the MAME options menu so it is useful in-game.
				handleControllerKey(OPTION_VALUE, event, digital_data);
				return true;
			}
			if ((event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_START || event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER
				|| event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_SELECT) && !Emulator.isInGame() && mm.getMainHelper().isAndroidTV()) {
				handleControllerKey(15, event, digital_data);
				return true;
			}

			if (hasCustomProfile) {
				return true;
			}

			return false;

		} else {
			// --- AUTODETECT PROCESSING ---
			int v = deviceMappings[event.getKeyCode()][dev];

			if (v != -1) {
				if (v == EXIT_VALUE) {
					if (event.getAction() == KeyEvent.ACTION_UP) {
						Emulator.setValue(Emulator.EXIT_GAME, 1);
						try { Thread.sleep(InputHandler.PRESS_WAIT); } catch (InterruptedException ignored) {}
						Emulator.setValue(Emulator.EXIT_GAME, 0);
					}
				} else if (v == OPTION_VALUE) {
					if (event.getAction() == KeyEvent.ACTION_UP  && !Emulator.isInOptions()) {
						Emulator.setInOptions(true);
						mm.showDialog(DialogHelper.DIALOG_OPTIONS);
					}
				} else {
					int action = event.getAction();
					if (action == KeyEvent.ACTION_DOWN) {
						digital_data[dev] |= v;
					} else if (action == KeyEvent.ACTION_UP) {
						digital_data[dev] &= ~v;
					}

					mm.getInputHandler().fixTiltCoin();
					Emulator.setDigitalData(dev, digital_data[dev]);
				}
				return true;
			}
			return false;
		}
	}


	// =========================================================================
	// ANDROID TV REMOTE DIRECTION PAD (dual-mode)
	// =========================================================================

	// TV remote D-pad behaviour modes (mirrors PrefsHelper.PREF_TV_DPAD_MODE).
	private static final int TV_DPAD_MODE_AUTO = 0;
	private static final int TV_DPAD_MODE_MOUSE = 1;
	private static final int TV_DPAD_MODE_DIRECT = 2;

	// Relative pointer step (pixels) applied per repeat tick while a direction is held.
	private static final int TV_MOUSE_STEP = 30;
	private static final long TV_MOUSE_REPEAT_MS = 50;

	// Accumulated pointer delta for the currently held directions (mouse mode).
	// tvDown tracks the *set* of physically held direction keys; tvMouseDx/Dy are
	// recomputed from that set, never incremented, so OS key-repeat cannot desync them.
	private int tvMouseDx = 0;
	private int tvMouseDy = 0;
	private boolean tvMouseScheduled = false;
	private final java.util.Set<Integer> tvDown = new java.util.HashSet<>();
	private final Handler tvMouseHandler = new Handler(Looper.getMainLooper());
	private final Runnable tvMouseTick = new Runnable() {
		@Override
		public void run() {
			if (tvMouseDx != 0 || tvMouseDy != 0) {
				Emulator.setMouseData(0, Emulator.MOUSE_MOVE_POINTER, 0, tvMouseDx, tvMouseDy);
				tvMouseHandler.postDelayed(this, TV_MOUSE_REPEAT_MS);
			} else {
				tvMouseScheduled = false;
			}
		}
	};

	/**
	 * True for direction-pad keys coming from a TV remote (SOURCE_DPAD / SOURCE_KEYBOARD)
	 * rather than from a real gamepad. Gated on Android TV or an explicit DPAD source so
	 * phones with a physical keyboard are never affected.
	 */
	private boolean isTvRemoteDpad(int keyCode, KeyEvent event) {
		switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_UP:
			case KeyEvent.KEYCODE_DPAD_DOWN:
			case KeyEvent.KEYCODE_DPAD_LEFT:
			case KeyEvent.KEYCODE_DPAD_RIGHT:
			case KeyEvent.KEYCODE_DPAD_CENTER:
				break;
			default:
				return false;
		}
		int src = event.getSource();
		boolean isGamepad = (src & (InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_CLASS_JOYSTICK)) != 0;
		if (isGamepad) return false; // owned by the main gamepad routing path
		boolean dpadSource = (src & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
		boolean kbSource = (src & InputDevice.SOURCE_KEYBOARD) == InputDevice.SOURCE_KEYBOARD;
		boolean tv = mm.getMainHelper().isAndroidTV();
		return dpadSource || (tv && kbSource);
	}

	/**
	 * Resolves the effective mode, expanding Auto into Mouse or Direct depending on
	 * whether a mouse-driven game is currently running.
	 */
	private int resolveTvDpadMode() {
		int mode = mm.getPrefsHelper().getTvDpadMode();
		if (mode == TV_DPAD_MODE_AUTO) {
			boolean mouseOn = mm.getPrefsHelper().isMouseEnabled()
				|| mm.getPrefsHelper().isTouchMouseEnabled();
			if (Emulator.isInGame() && mouseOn) {
				return TV_DPAD_MODE_MOUSE;
			}
			return TV_DPAD_MODE_DIRECT;
		}
		return mode;
	}

	/**
	 * Dispatches a TV remote D-pad key according to the selected dual mode.
	 * Returns true when the event was consumed.
	 */
	private boolean handleTvDpad(int keyCode, KeyEvent event, int[] digital_data) {
		int action = event.getAction();
		int mode = resolveTvDpadMode();

		switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_CENTER:
				if (mode == TV_DPAD_MODE_MOUSE) {
					// Mouse-mode OK: click the emulated mouse button.
					if (action == KeyEvent.ACTION_DOWN)
						Emulator.setMouseData(0, Emulator.MOUSE_BTN_DOWN, 1, -1, -1);
					else
						Emulator.setMouseData(0, Emulator.MOUSE_BTN_UP, 1, -1, -1);
					return true;
				}
				// Direct-mode OK (Android TV "select" convention):
				//  - Frontend / OSD menus: confirm selection (Enter -> MAME UI_SELECT)
				//  - In game: open the MAME4droid options menu
				if (Emulator.isInGame()) {
					handleControllerKey(OPTION_VALUE, event, digital_data);
				} else {
					Emulator.setKeyData(KeyEvent.KEYCODE_ENTER,
						action == KeyEvent.ACTION_DOWN ? Emulator.KEY_DOWN : Emulator.KEY_UP,
						(char) 0);
				}
				return true;

			case KeyEvent.KEYCODE_DPAD_UP:
			case KeyEvent.KEYCODE_DPAD_DOWN:
			case KeyEvent.KEYCODE_DPAD_LEFT:
			case KeyEvent.KEYCODE_DPAD_RIGHT:
				if (mode == TV_DPAD_MODE_MOUSE) {
					return handleTvDpadMouse(keyCode, action);
				}
				return handleTvDpadDirect(keyCode, action, digital_data);

			default:
				return false;
		}
	}

	/**
	 * Direct-key mode: feed the D-pad into player-1 digital data exactly like a
	 * gamepad stick, so the MAME OSD frontend navigates with MAME's default UI input.
	 */
	private boolean handleTvDpadDirect(int keyCode, int action, int[] digital_data) {
		int mask = 0;
		switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_UP:    mask = IController.UP_VALUE;    break;
			case KeyEvent.KEYCODE_DPAD_DOWN:  mask = IController.DOWN_VALUE;  break;
			case KeyEvent.KEYCODE_DPAD_LEFT:  mask = IController.LEFT_VALUE;  break;
			case KeyEvent.KEYCODE_DPAD_RIGHT: mask = IController.RIGHT_VALUE; break;
		}
		if (mask == 0) return false;
		if (action == KeyEvent.ACTION_DOWN) digital_data[0] |= mask;
		else digital_data[0] &= ~mask;
		Emulator.setDigitalData(0, digital_data[0]);
		return true;
	}

	/**
	 * Mouse-pointer mode: drive the emulated mouse cursor. An immediate step is sent
	 * on the first key-down for responsiveness, and a repeat tick keeps moving while
	 * the key is held so the cursor glides smoothly across the screen.
	 *
	 * Held directions are tracked in a {@code Set} (tvDown) rather than by accumulating
	 * deltas. Android delivers repeated ACTION_DOWN events while a key is held
	 * (getRepeatCount() increments); an incremental += would desync the accumulated delta
	 * from the real held keys, so on release tvMouseDx/Dy could never return to 0 and the
	 * tick would drift forever. Recomputing from the held-set on every press/release keeps
	 * the delta exactly in sync with the physical buttons.
	 */
	private void recomputeTvMouse() {
		int dx = 0, dy = 0;
		for (int k : tvDown) {
			if (k == KeyEvent.KEYCODE_DPAD_UP)        dy -= TV_MOUSE_STEP;
			else if (k == KeyEvent.KEYCODE_DPAD_DOWN)  dy += TV_MOUSE_STEP;
			else if (k == KeyEvent.KEYCODE_DPAD_LEFT)  dx -= TV_MOUSE_STEP;
			else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) dx += TV_MOUSE_STEP;
		}
		tvMouseDx = dx;
		tvMouseDy = dy;
	}

	private boolean handleTvDpadMouse(int keyCode, int action) {
		int dx = 0, dy = 0;
		switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_UP:    dy = -TV_MOUSE_STEP; break;
			case KeyEvent.KEYCODE_DPAD_DOWN:  dy =  TV_MOUSE_STEP; break;
			case KeyEvent.KEYCODE_DPAD_LEFT:  dx = -TV_MOUSE_STEP; break;
			case KeyEvent.KEYCODE_DPAD_RIGHT: dx =  TV_MOUSE_STEP; break;
			default: return false;
		}
		if (action == KeyEvent.ACTION_DOWN) {
			// Set.add() is idempotent: OS key-repeat DOWNs are ignored, so the held
			// delta stays in sync with the physical buttons and never drifts.
			if (tvDown.add(keyCode)) {
				recomputeTvMouse();
				Emulator.setMouseData(0, Emulator.MOUSE_MOVE_POINTER, 0, dx, dy);
				if (!tvMouseScheduled) {
					tvMouseScheduled = true;
					tvMouseHandler.postDelayed(tvMouseTick, TV_MOUSE_REPEAT_MS);
				}
			}
		} else { // ACTION_UP
			if (tvDown.remove(keyCode)) {
				recomputeTvMouse();
				if (tvDown.isEmpty()) {
					tvMouseScheduled = false;
					tvMouseHandler.removeCallbacks(tvMouseTick);
				}
			}
		}
		return true;
	}



	final public float rad2degree(float r) {
		return ((r * 180.0f) / MY_PI);
	}

	final public float getAngle(float x, float y) {
		float ang = rad2degree((float) Math.atan2(x, y));
		if (ang < 0.0f) ang += 360.0f;
		return ang;
	}

	final public float getMagnitude(float x, float y) {
		return (float) Math.sqrt((x * x) + (y * y));
	}

	protected float processAxis(InputDevice.MotionRange range, float axisvalue) {
		float absaxisvalue = Math.abs(axisvalue);
		float deadzone = range.getFlat();

		if (absaxisvalue <= deadzone) return 0.0f;

		float normalizedvalue;
		if (axisvalue < 0.0f) {
			normalizedvalue = absaxisvalue / range.getMin();
		} else {
			normalizedvalue = absaxisvalue / range.getMax();
		}
		return normalizedvalue;
	}

	final public float getAxisValue(int axis, MotionEvent event, int historyPos) {
		float value = 0.0f;
		InputDevice device = event.getDevice();
		if (device != null) {
			InputDevice.MotionRange range = device.getMotionRange(axis, event.getSource());
			if (range != null) {
				float axisValue;
				if (historyPos >= 0) {
					axisValue = event.getHistoricalAxisValue(axis, historyPos);
				} else {
					axisValue = event.getAxisValue(axis);
				}
				value = this.processAxis(range, axisValue);
			}
		}
		return value;
	}

	protected boolean hasSignificantMovement(MotionEvent event, float threshold) {
		int[] axes = {
			MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
			MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y, MotionEvent.AXIS_GAS, MotionEvent.AXIS_BRAKE
		};

		for (int axis : axes) {
			if (Math.abs(getAxisValue(axis, event, -1)) > threshold) {
				return true;
			}
		}
		return false;
	}

	public boolean genericMotion(MotionEvent event, int[] digital_data) {
		if (((event.getSource() & (InputDevice.SOURCE_CLASS_JOYSTICK | InputDevice.SOURCE_GAMEPAD)) == 0)
			|| (event.getAction() != MotionEvent.ACTION_MOVE)) {
			return false;
		}

		InputDevice device = event.getDevice();
		if (device == null) return false; // Anti-crash protection

		if (hasSignificantMovement(event, 0.20f)) {
			if (!joystickMotion) {
				joystickMotion = true;
				mm.getMainHelper().updateMAME4droid();
			}
			// Centralize device registration upon significant analog stick movement
			checkAndRegisterDevice(event.getDevice());
		}

		int historySize = event.getHistorySize();
		for (int i = 0; i < historySize; i++) {
			processStickInput(event, i, digital_data);
		}

		return processStickInput(event, -1, digital_data);
	}

	protected boolean processStickInput(MotionEvent event, int historyPos, int[] digital_data) {
		int ways = mm.getPrefsHelper().getStickWays();
		if (ways == -1) ways = Emulator.getValue(Emulator.NUMWAYS);
		boolean b = Emulator.isInGameButNotInMenu();

		int dev = getDevice(event.getDevice(), false);

		if (dev == -1) { // It's a generic controller, find its dynamic slot
			for (int i = 0; i < MAX_DEVICES; i++) {
				if (deviceIDs[i] == event.getDevice().getId()) {
					dev = i;
					break;
				}
			}
		}

		// Prevent out-of-bounds overflow for extra unseated controllers (e.g. 5th controller)
		if (dev == -1) {
			return false;
		}

		int joy = dev;
		newinput[joy] = 0;

		float deadZone = 0.2f;
		switch (mm.getPrefsHelper().getGamepadDZ()) {
			case 1: deadZone = 0.01f; break;
			case 2: deadZone = 0.15f; break;
			case 3: deadZone = 0.2f; break;
			case 4: deadZone = 0.3f; break;
			case 5: deadZone = 0.5f; break;
		}

		float x = 0.0f, y = 0.0f, mag = 0.0f;

		for (int i = 0; i < 2; i++) {
			if (i == 0 && mm.getInputHandler().getTiltSensor().isEnabled() && Emulator.isInGameButNotInMenu())
				continue;

			if (i == 0) {
				x = getAxisValue(MotionEvent.AXIS_X, event, historyPos);
				y = getAxisValue(MotionEvent.AXIS_Y, event, historyPos);
			} else {
				x = getAxisValue(MotionEvent.AXIS_HAT_X, event, historyPos);
				y = getAxisValue(MotionEvent.AXIS_HAT_Y, event, historyPos);
			}

			mag = getMagnitude(x, y);

			if (mag >= deadZone) {
				if (i == 0) {
					if (mm.getPrefsHelper().getControllerType() != PrefsHelper.PREF_DIGITAL_STICK) {
						// Constrain the pure-analog OUTPUT to the allowed dirs:
						// 2-way -> horizontal; 4-way and any menu (!b) -> dominant
						// cardinal axis only; 8-way/free keep full range.
						float ox = x, oy = y;
						if (ways == 2 && b) {
							oy = 0.0f;
						} else if (ways == 4 || !b) {
							if (Math.abs(ox) >= Math.abs(oy)) oy = 0.0f; else ox = 0.0f;
						}
						Emulator.setAnalogData(Emulator.LEFT_STICK_DATA, joy, ox, oy * -1.0f);
						continue;
					}
				}

				float v = getAngle(x, y);

				// Arcade stick way-restrictions
				if (ways == 2 && b) {
					if (v < 180) newinput[joy] |= RIGHT_VALUE;
					else if (v >= 180) newinput[joy] |= LEFT_VALUE;
				} else if (ways == 4 || !b) {
					if (v >= 315 || v < 45) newinput[joy] |= DOWN_VALUE;
					else if (v >= 45 && v < 135) newinput[joy] |= RIGHT_VALUE;
					else if (v >= 135 && v < 225) newinput[joy] |= UP_VALUE;
					else if (v >= 225 && v < 315) newinput[joy] |= LEFT_VALUE;
				} else { // 8-way
					if (v >= 330 || v < 30) {
						newinput[joy] |= DOWN_VALUE;
					} else if (v >= 30 && v < 60) {
						newinput[joy] |= DOWN_VALUE;
						newinput[joy] |= RIGHT_VALUE;
					} else if (v >= 60 && v < 120) {
						newinput[joy] |= RIGHT_VALUE;
					} else if (v >= 120 && v < 150) {
						newinput[joy] |= RIGHT_VALUE;
						newinput[joy] |= UP_VALUE;
					} else if (v >= 150 && v < 210) {
						newinput[joy] |= UP_VALUE;
					} else if (v >= 210 && v < 240) {
						newinput[joy] |= UP_VALUE;
						newinput[joy] |= LEFT_VALUE;
					} else if (v >= 240 && v < 300) {
						newinput[joy] |= LEFT_VALUE;
					} else if (v >= 300 && v < 330) {
						newinput[joy] |= LEFT_VALUE;
						newinput[joy] |= DOWN_VALUE;
					}
				}
			} else {
				if (i == 0) {
					Emulator.setAnalogData(Emulator.LEFT_STICK_DATA, joy, 0, 0);
				}
			}
		}

		if (!mm.getPrefsHelper().isDisabledRightStick() && Emulator.isInGame()) {
			x = getAxisValue(MotionEvent.AXIS_Z, event, historyPos);
			y = getAxisValue(MotionEvent.AXIS_RZ, event, historyPos) * -1;
			mag = getMagnitude(x, y);

			if (mag >= deadZone) {
				Emulator.setAnalogData(Emulator.RIGHT_STICK_DATA, joy, x, y);
			} else {
				Emulator.setAnalogData(Emulator.RIGHT_STICK_DATA, joy, 0.0f, 0.0f);
			}
		}

		x = getAxisValue(MotionEvent.AXIS_GAS, event, historyPos);
		y = getAxisValue(MotionEvent.AXIS_BRAKE, event, historyPos);
		Emulator.setAnalogData(Emulator.TRIGGER_DATA, joy, (x * 2.0f) - 1.0f, (y * 2.0f) - 1.0f);

		digital_data[joy] &= ~(oldinput[joy] & ~newinput[joy]);
		digital_data[joy] |= newinput[joy];

		mm.getInputHandler().fixTiltCoin();
		Emulator.setDigitalData(joy, digital_data[joy]);

		oldinput[joy] = newinput[joy];

		return true;
	}


	// =========================================================================
	// AUTODETECT SYSTEM (Hardcoded profiles)
	// =========================================================================

	protected void mapDPAD(int id) {
		deviceMappings[KeyEvent.KEYCODE_DPAD_UP][id] = UP_VALUE;
		deviceMappings[KeyEvent.KEYCODE_DPAD_DOWN][id] = DOWN_VALUE;
		deviceMappings[KeyEvent.KEYCODE_DPAD_LEFT][id] = LEFT_VALUE;
		deviceMappings[KeyEvent.KEYCODE_DPAD_RIGHT][id] = RIGHT_VALUE;
	}

	protected void mapL1R1(int id) {
		deviceMappings[KeyEvent.KEYCODE_BUTTON_L1][id] = E_VALUE;
		deviceMappings[KeyEvent.KEYCODE_BUTTON_R1][id] = F_VALUE;
	}

	protected void mapTHUMBS(int id) {
		deviceMappings[KeyEvent.KEYCODE_BUTTON_THUMBL][id] = START_VALUE;
		deviceMappings[KeyEvent.KEYCODE_BUTTON_THUMBR][id] = COIN_VALUE;
	}

	protected void mapSelectStart(int id) {
		deviceMappings[KeyEvent.KEYCODE_BUTTON_SELECT][id] = EXIT_VALUE;
		deviceMappings[KeyEvent.KEYCODE_BUTTON_START][id] = OPTION_VALUE;
	}

	protected int detectDevice(InputDevice device) {
		boolean detected = false;
		int id = -1;

		for (int i = 0; i < MAX_DEVICES && id == -1; i++) {
			if (deviceIDs[i] == -1) id = i;
		}

		if (id == -1 || device == null || banDev == null) return -1;
		if (banDev.get(device.getId()) == 1) return -1;

		final String name = device.getName();

		if (Emulator.isDebug()) {
			String msg = mm.getString(R.string.input_device_detected, name);
			new WarnWidget.WarnWidgetHelper(mm, msg, 3, Color.GREEN, true);
		}

		CharSequence desc = "";

		if (name.contains("PLAYSTATION(R)3") || name.indexOf("Dualshock3") != -1
			|| name.contains("Sixaxis") || name.contains("Gasia,Co")) {

			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_Y][id] = D_VALUE;

			mapDPAD(id);
			mapL1R1(id);
			mapTHUMBS(id);
			mapSelectStart(id);

			deviceMappings[KeyEvent.KEYCODE_BACK][id] = EXIT_VALUE;
			desc = "Sixaxis";
			detected = true;

		} else if (name.contains("Gamepad 0") || name.contains("Gamepad 1") || name.contains("Gamepad 2")) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_Y][id] = D_VALUE;

			mapDPAD(id);
			mapL1R1(id);
			mapTHUMBS(id);
			mapSelectStart(id);

			desc = "Gamepad";
			detected = true;

		} else if (name.contains("nvidia_joypad") || name.contains("NVIDIA Controller")) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_Y][id] = D_VALUE;

			mapL1R1(id);
			mapTHUMBS(id);

			deviceMappings[KeyEvent.KEYCODE_BUTTON_START][id] = OPTION_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BACK][id] = EXIT_VALUE;

			desc = "NVIDIA Shield";
			detected = true;

		} else if (name.contains("ipega Extending")) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_Y][id] = D_VALUE;

			mapL1R1(id);
			mapTHUMBS(id);

			deviceMappings[KeyEvent.KEYCODE_BUTTON_START][id] = OPTION_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_SELECT][id] = EXIT_VALUE;

			desc = "Ipega Extending Game";
			detected = true;

		} else if (isXboxName(name)) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_Y][id] = D_VALUE;

			mapDPAD(id);
			mapL1R1(id);
			mapTHUMBS(id);
			mapSelectStart(id);

			deviceMappings[KeyEvent.KEYCODE_BACK][id] = EXIT_VALUE;
			desc = "XBox";
			// Converge the connectivity flags to the single canonical writer
			// (refreshGamepadConnected) instead of writing xboxConnected directly
			// here. This keeps the flag correct on every entry path (add vs first
			// key press) and avoids a second unsynchronized write site.
			refreshGamepadConnected();
			detected = true;

		} else if (name.contains("Logitech") && name.contains("Dual Action")) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = D_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_Y][id] = A_VALUE;

			mapL1R1(id);
			mapTHUMBS(id);
			mapSelectStart(id);

			desc = "Dual Action";
			detected = true;

		} else if (name.contains("Logitech") && name.contains("RumblePad 2")) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_2][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_1][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_3][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_4][id] = D_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_9][id] = COIN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_10][id] = START_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_5][id] = E_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_6][id] = F_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_11][id] = OPTION_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_12][id] = EXIT_VALUE;

			desc = "Rumblepad 2";
			detected = true;

		} else if (name.contains("Logitech") && name.contains("Precision")) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_2][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_1][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_3][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_4][id] = D_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_5][id] = E_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_6][id] = F_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_7][id] = OPTION_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_8][id] = EXIT_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_9][id] = COIN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_10][id] = START_VALUE;

			desc = "Logitech Precision";
			detected = true;

		} else if (name.contains("TTT THT Arcade console 2P USB Play")) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_1][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_2][id] = D_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_5][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_6][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_3][id] = E_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_7][id] = F_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_4][id] = OPTION_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_8][id] = COIN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_9][id] = START_VALUE;

			desc = "TTT THT Arcade";
			detected = true;

		} else if (name.contains("TOMMO NEOGEOX Arcade Stick")) {
			mapDPAD(id);

			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_C][id] = D_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_R2][id] = COIN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_L2][id] = START_VALUE;

			desc = "TOMMO Neogeo X Arcade";
			detected = true;

		} else if (name.contains("Onlive Wireless Controller")) {
			mapDPAD(id);

			deviceMappings[KeyEvent.KEYCODE_BUTTON_Y][id] = D_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = A_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_L1][id] = E_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_R1][id] = F_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_L1][id] = COIN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BACK][id] = START_VALUE;

			desc = "Onlive Wireless";
			detected = true;

		} else if (name.contains("MadCatz") && name.contains("PC USB Wired Stick")) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_C][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = D_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_Y][id] = E_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_Z][id] = E_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_L1][id] = E_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_R1][id] = F_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_L2][id] = COIN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_R2][id] = START_VALUE;

			desc = "Madcatz PC USB Stick";
			detected = true;

		} else if (name.contains("Logicool") && name.contains("RumblePad 2")) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_C][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = D_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = C_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_Y][id] = E_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_Z][id] = F_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_L1][id] = OPTION_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_R1][id] = EXIT_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_L2][id] = COIN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_R2][id] = START_VALUE;

			desc = "Logicool Rumblepad 2";
			detected = true;

		} else if (name.contains("Zeemote") && name.contains("Steelseries free")) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_Y][id] = D_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = C_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_MODE][id] = COIN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_START][id] = START_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_L1][id] = E_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_R1][id] = F_VALUE;

			desc = "Zeemote Steelseries";
			detected = true;

		} else if (name.contains("HuiJia  USB GamePad")) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_3][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_4][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_2][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_1][id] = D_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_7][id] = E_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_8][id] = F_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_9][id] = COIN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_10][id] = START_VALUE;

			desc = "Huijia USB SNES";
			detected = true;

		} else if (name.contains("Smartjoy Family Super Smartjoy 2")) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_3][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_4][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_2][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_1][id] = D_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_7][id] = E_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_8][id] = F_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_5][id] = COIN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_6][id] = START_VALUE;

			desc = "Super Smartjoy";
			detected = true;

		} else if (name.contains("Jess Tech Dual Analog Rumble Pad")) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_3][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_1][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_4][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_2][id] = D_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_5][id] = E_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_7][id] = F_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_6][id] = OPTION_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_8][id] = EXIT_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_11][id] = COIN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_12][id] = START_VALUE;

			detected = true;

		} else if (name.contains("Microsoft") && name.contains("Dual Strike")) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_4][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_2][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_3][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_1][id] = D_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_7][id] = E_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_8][id] = F_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_9][id] = OPTION_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_6][id] = COIN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_5][id] = START_VALUE;

			desc = "MS Dual Strike";
			detected = true;

		} else if (name.contains("Microsoft") && name.contains("SideWinder")) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_Y][id] = D_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_L1][id] = E_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_R1][id] = F_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_Z][id] = OPTION_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_C][id] = EXIT_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_11][id] = COIN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_12][id] = START_VALUE;

			desc = "MS Sidewinder";
			detected = true;

		} else if (name.contains("WiseGroup") &&
			(name.contains("JC-PS102U") || name.contains("TigerGame")) ||
			name.contains("Game Controller Adapter") || name.contains("Dual USB Joypad") ||
			name.contains("Twin USB Joystick")) {

			deviceMappings[KeyEvent.KEYCODE_BUTTON_13][id] = UP_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_15][id] = DOWN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_16][id] = LEFT_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_14][id] = RIGHT_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_4][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_1][id] = D_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_3][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_2][id] = A_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_7][id] = E_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_8][id] = F_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_5][id] = OPTION_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_6][id] = EXIT_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_10][id] = COIN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_9][id] = START_VALUE;

			desc = "PlayStation2";
			detected = true;

		} else if (name.contains("MOGA") || name.contains("Moga")) {
			mapDPAD(id);

			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_Y][id] = D_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_L1][id] = E_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_R1][id] = F_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_SELECT][id] = COIN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_START][id] = START_VALUE;

			desc = "MOGA";
			detected = true;

		} else if (name.contains("OUYA Game Controller")) {
			mapDPAD(id);

			deviceMappings[KeyEvent.KEYCODE_BUTTON_Y][id] = D_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = B_VALUE;

			deviceMappings[KeyEvent.KEYCODE_MENU][id] = OPTION_VALUE;

			mapL1R1(id);
			mapTHUMBS(id);

			desc = "OUYA";
			detected = true;

		} else if (name.contains("DragonRise")) {
			mapDPAD(id);

			deviceMappings[KeyEvent.KEYCODE_BUTTON_2][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_3][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_4][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_1][id] = D_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_5][id] = E_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_6][id] = F_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_7][id] = COIN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_8][id] = START_VALUE;

			desc = "DragonRise";
			detected = true;

		} else if (name.contains("Thrustmaster T Mini")) {
			mapDPAD(id);

			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = D_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_C][id] = A_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_Y][id] = E_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_Z][id] = F_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_L2][id] = OPTION_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_R1][id] = EXIT_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_R2][id] = COIN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_L2][id] = START_VALUE;

			desc = "Thrustmaster T Mini";
			detected = true;

		} else if (name.contains("ADC joystick")) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_Y][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = D_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_L2][id] = E_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_R2][id] = F_VALUE;

			mapDPAD(id);
			mapL1R1(id);

			deviceMappings[KeyEvent.KEYCODE_BUTTON_SELECT][id] = COIN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_START][id] = START_VALUE;

			desc = "JXD S7800";
			detected = true;

		} else if (name.contains("Green Throttle Atlas")) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_Y][id] = D_VALUE;

			mapDPAD(id);
			mapL1R1(id);
			mapTHUMBS(id);
			mapSelectStart(id);

			deviceMappings[KeyEvent.KEYCODE_BACK][id] = EXIT_VALUE;

			desc = "Green Throttle";
			detected = true;

		} else if (name.contains("joy_key") && mm.getMainHelper().getDeviceDetected() == MainHelper.DEVICE_AGAMEPAD2) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_Y][id] = D_VALUE;

			deviceMappings[KeyEvent.KEYCODE_BUTTON_L2][id] = E_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_R2][id] = F_VALUE;

			mapDPAD(id);
			mapL1R1(id);

			deviceMappings[KeyEvent.KEYCODE_BUTTON_SELECT][id] = COIN_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_START][id] = START_VALUE;

			desc = "Archos Gamepad 2";
			detected = true;

		} else if (name.contains("NYKO PLAYPAD") ||
			(name.contains("Broadcom Bluetooth HID") && mm.getMainHelper().getDeviceDetected() == MainHelper.DEVICE_SHIELD)) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_Y][id] = D_VALUE;

			mapL1R1(id);
			mapTHUMBS(id);

			deviceMappings[KeyEvent.KEYCODE_BUTTON_START][id] = OPTION_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BACK][id] = EXIT_VALUE;

			desc = "NYKO PLAYPAD";
			detected = true;

		} else if (name.contains("BSP-D8")) {
			deviceMappings[KeyEvent.KEYCODE_BUTTON_A][id] = B_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_B][id] = A_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_X][id] = C_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_Y][id] = D_VALUE;

			mapDPAD(id);
			mapL1R1(id);
			mapTHUMBS(id);

			deviceMappings[KeyEvent.KEYCODE_BUTTON_SELECT][id] = OPTION_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BUTTON_START][id] = EXIT_VALUE;
			deviceMappings[KeyEvent.KEYCODE_BACK][id] = EXIT_VALUE;

			desc = "BSP-D8";
			detected = true;
		}

		if (detected) {
			Log.d(TAG,"Controller detected: " + device.getName());
			deviceIDs[id] = device.getId();
			id++;

			if (id == 1) mm.getMainHelper().updateMAME4droid();

			CharSequence text = mm.getString(R.string.controller_detected_as, desc, id);
			new WarnWidget.WarnWidgetHelper(mm, text.toString(), 3, Color.GREEN, true);

			return id - 1;
		} else {
			banDev.append(device.getId(), 1);
		}

		return -1;
	}
}
