/*
 * Copyright 2018 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.ar.sceneform.samples.solarsystem

import android.net.Uri
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import com.google.ar.core.Anchor
import com.google.ar.core.Frame
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.Trackable
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableException
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.ArSceneView
import com.google.ar.sceneform.HitTestResult
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.rendering.ViewRenderable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using the
 * ARCore and Sceneform APIs.
 */
class SolarActivity : AppCompatActivity() {
    private var installRequested: Boolean = false

    private var gestureDetector: GestureDetector? = null
    private var loadingMessageSnackbar: Snackbar? = null

    private var arSceneView: ArSceneView? = null

    private var sunRenderable: ModelRenderable? = null
    private var mercuryRenderable: ModelRenderable? = null
    private var venusRenderable: ModelRenderable? = null
    private var earthRenderable: ModelRenderable? = null
    private var lunaRenderable: ModelRenderable? = null
    private var marsRenderable: ModelRenderable? = null
    private var jupiterRenderable: ModelRenderable? = null
    private var saturnRenderable: ModelRenderable? = null
    private var uranusRenderable: ModelRenderable? = null
    private var neptuneRenderable: ModelRenderable? = null
    private var solarControlsRenderable: ViewRenderable? = null

    private val solarSettings = SolarSettings()

    // True once scene is loaded
    private var hasFinishedLoading = false

    // True once the scene has been placed.
    private var hasPlacedSolarSystem = false

    override// CompletableFuture requires api level 24
    fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!DemoUtils.checkIsSupportedDeviceOrFinish(this)) {
            // Not a supported device.
            return
        }

        setContentView(R.layout.activity_solar)
        arSceneView = findViewById(R.id.ar_scene_view)

        // Build all the planet models.
        val sunStage = ModelRenderable.builder().setSource(this, Uri.parse("Sol.sfb")).build()
        val mercuryStage = ModelRenderable.builder().setSource(this, Uri.parse("Mercury.sfb")).build()
        val venusStage = ModelRenderable.builder().setSource(this, Uri.parse("Venus.sfb")).build()
        val earthStage = ModelRenderable.builder().setSource(this, Uri.parse("Earth.sfb")).build()
        val lunaStage = ModelRenderable.builder().setSource(this, Uri.parse("Luna.sfb")).build()
        val marsStage = ModelRenderable.builder().setSource(this, Uri.parse("Mars.sfb")).build()
        val jupiterStage = ModelRenderable.builder().setSource(this, Uri.parse("Jupiter.sfb")).build()
        val saturnStage = ModelRenderable.builder().setSource(this, Uri.parse("Saturn.sfb")).build()
        val uranusStage = ModelRenderable.builder().setSource(this, Uri.parse("Uranus.sfb")).build()
        val neptuneStage = ModelRenderable.builder().setSource(this, Uri.parse("Neptune.sfb")).build()

        // Build a renderable from a 2D View.
        val solarControlsStage = ViewRenderable.builder().setView(this, R.layout.solar_controls).build()

        CompletableFuture.allOf(
            sunStage,
            mercuryStage,
            venusStage,
            earthStage,
            lunaStage,
            marsStage,
            jupiterStage,
            saturnStage,
            uranusStage,
            neptuneStage,
            solarControlsStage)
            .handle<Any> { notUsed, throwable ->
                // When you build a Renderable, Sceneform loads its resources in the background while
                // returning a CompletableFuture. Call handle(), thenAccept(), or check isDone()
                // before calling get().

                if (throwable != null) {
                    DemoUtils.displayError(this, "Unable to load renderable", throwable)
                } else {

                    try {
                        sunRenderable = sunStage.get()
                        mercuryRenderable = mercuryStage.get()
                        venusRenderable = venusStage.get()
                        earthRenderable = earthStage.get()
                        lunaRenderable = lunaStage.get()
                        marsRenderable = marsStage.get()
                        jupiterRenderable = jupiterStage.get()
                        saturnRenderable = saturnStage.get()
                        uranusRenderable = uranusStage.get()
                        neptuneRenderable = neptuneStage.get()
                        solarControlsRenderable = solarControlsStage.get()

                        // Everything finished loading successfully.
                        hasFinishedLoading = true

                    } catch (ex: InterruptedException) {
                        DemoUtils.displayError(this, "Unable to load renderable", ex)
                    } catch (ex: ExecutionException) {
                        DemoUtils.displayError(this, "Unable to load renderable", ex)
                    }
                }

                null
            }

        // Set up a tap gesture detector.
        gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    onSingleTap(e)
                    return true
                }

                override fun onDown(e: MotionEvent): Boolean {
                    return true
                }
            })

        // Set a touch listener on the Scene to listen for taps.
        arSceneView!!
            .scene
            .setOnTouchListener { hitTestResult: HitTestResult, event: MotionEvent ->
                // If the solar system hasn't been placed yet, detect a tap and then check to see if
                // the tap occurred on an ARCore plane to place the solar system.

                 if (!hasPlacedSolarSystem) {
                    val toReturn = gestureDetector?.onTouchEvent(event) ?: false
                    toReturn
                } else {
                    false
                }

                // Otherwise return false so that the touch event can propagate to the scene.

            }

        // Set an update listener on the Scene that will hide the loading message once a Plane is
        // detected.
        arSceneView!!
            .scene
            .addOnUpdateListener { frameTime ->
                if (loadingMessageSnackbar == null) {
                    return@addOnUpdateListener
                }

                val frame = arSceneView?.arFrame ?: return@addOnUpdateListener

                if (frame.camera.trackingState != TrackingState.TRACKING) {
                    return@addOnUpdateListener
                }

                for (plane in frame.getUpdatedTrackables(Plane::class.java)) {
                    if (plane.trackingState == TrackingState.TRACKING) {
                        hideLoadingMessage()
                    }
                }
            }

        // Lastly request CAMERA permission which is required by ARCore.
        DemoUtils.requestCameraPermission(this, RC_PERMISSIONS)
    }

    override fun onResume() {
        super.onResume()
        if (arSceneView == null) {
            return
        }

        if (arSceneView!!.session == null) {
            // If the session wasn't created yet, don't resume rendering.
            // This can happen if ARCore needs to be updated or permissions are not granted yet.
            try {
                val session = DemoUtils.createArSession(this, installRequested)
                if (session == null) {
                    installRequested = DemoUtils.hasCameraPermission(this)
                    return
                } else {
                    arSceneView!!.setupSession(session)
                }
            } catch (e: UnavailableException) {
                DemoUtils.handleSessionException(this, e)
            }

        }

        try {
            arSceneView!!.resume()
        } catch (ex: CameraNotAvailableException) {
            DemoUtils.displayError(this, "Unable to get camera", ex)
            finish()
            return
        }

        if (arSceneView!!.session != null) {
            showLoadingMessage()
        }
    }

    public override fun onPause() {
        super.onPause()
        if (arSceneView != null) {
            arSceneView!!.pause()
        }
    }

    public override fun onDestroy() {
        super.onDestroy()
        if (arSceneView != null) {
            arSceneView!!.destroy()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, results: IntArray) {
        if (!DemoUtils.hasCameraPermission(this)) {
            if (!DemoUtils.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                DemoUtils.launchPermissionSettings(this)
            } else {
                Toast.makeText(
                    this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                    .show()
            }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Standard Android full-screen functionality.
            window
                .decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun onSingleTap(tap: MotionEvent) {
        if (!hasFinishedLoading) {
            // We can't do anything yet.
            return
        }

        val frame = arSceneView!!.arFrame
        if (frame != null) {
            if (!hasPlacedSolarSystem && tryPlaceSolarSystem(tap, frame)) {
                hasPlacedSolarSystem = true
            }
        }
    }

    private fun tryPlaceSolarSystem(tap: MotionEvent?, frame: Frame): Boolean {
        if (tap != null && frame.camera.trackingState == TrackingState.TRACKING) {
            for (hit in frame.hitTest(tap)) {
                val trackable = hit.trackable
                if (trackable is Plane && trackable.isPoseInPolygon(hit.hitPose)) {
                    // Create the Anchor.
                    val anchora = hit.createAnchor()

                    val scene = scene {
                        anchorNode {
                            anchor = anchora
                            node {
                                node {
                                    position = Vector3(0.0f, 0.5f, 0.0f)
                                    scale = Vector3(0.5f, 0.5f, 0.5f)
                                    model = sunRenderable
                                    node {
                                        position = Vector3(1.0f * AU_TO_METERS, 0.0f, 0.0f)
                                        scale = Vector3(0.05f, 0.05f, 0.05f)
                                        model = earthRenderable
                                    }
                                }
                            }
                        }
                    }

                    arSceneView!!.scene setTo scene
                    return true
                }
            }
        }

        return false
    }

    private fun createSolarSystem(): Node {
        val base = Node()

        val sun = Node()
        sun.setParent(base)
        sun.localPosition = Vector3(0.0f, 0.5f, 0.0f)

        val sunVisual = Node()
        sunVisual.setParent(sun)
        sunVisual.renderable = sunRenderable
        sunVisual.localScale = Vector3(0.5f, 0.5f, 0.5f)

        val solarControls = Node()
        solarControls.setParent(sun)
        solarControls.renderable = solarControlsRenderable
        solarControls.localPosition = Vector3(0.0f, 0.25f, 0.0f)

        val solarControlsView = solarControlsRenderable!!.view
        val orbitSpeedBar = solarControlsView.findViewById<SeekBar>(R.id.orbitSpeedBar)
        orbitSpeedBar.progress = (solarSettings.orbitSpeedMultiplier * 10.0f).toInt()
        orbitSpeedBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val ratio = progress.toFloat() / orbitSpeedBar.max.toFloat()
                    solarSettings.orbitSpeedMultiplier = ratio * 10.0f
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })

        val rotationSpeedBar = solarControlsView.findViewById<SeekBar>(R.id.rotationSpeedBar)
        rotationSpeedBar.progress = (solarSettings.rotationSpeedMultiplier * 10.0f).toInt()
        rotationSpeedBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    val ratio = progress.toFloat() / rotationSpeedBar.max.toFloat()
                    solarSettings.rotationSpeedMultiplier = ratio * 10.0f
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {}

                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })

        // Toggle the solar controls on and off by tapping the sun.
        sunVisual.setOnTapListener { hitTestResult, motionEvent -> solarControls.isEnabled = !solarControls.isEnabled }

        createPlanet("Mercury", sun, 0.4f, 47f, mercuryRenderable, 0.019f)

        createPlanet("Venus", sun, 0.7f, 35f, venusRenderable, 0.0475f)

        val earth = createPlanet("Earth", sun, 1.0f, 29f, earthRenderable, 0.05f)

        createPlanet("Moon", earth, 0.15f, 100f, lunaRenderable, 0.018f)

        createPlanet("Mars", sun, 1.5f, 24f, marsRenderable, 0.0265f)

        createPlanet("Jupiter", sun, 2.2f, 13f, jupiterRenderable, 0.16f)

        createPlanet("Saturn", sun, 3.5f, 9f, saturnRenderable, 0.1325f)

        createPlanet("Uranus", sun, 5.2f, 7f, uranusRenderable, 0.1f)

        createPlanet("Neptune", sun, 6.1f, 5f, neptuneRenderable, 0.074f)

        return base
    }

    private fun createPlanet(
        name: String,
        parent: Node,
        auFromParent: Float,
        orbitDegreesPerSecond: Float,
        renderable: ModelRenderable?,
        planetScale: Float): Node {
        // Orbit is a rotating node with no renderable positioned at the sun.
        // The planet is positioned relative to the orbit so that it appears to rotate around the sun.
        // This is done instead of making the sun rotate so each planet can orbit at its own speed.
        val orbit = RotatingNode(solarSettings, true)
        orbit.setDegreesPerSecond(orbitDegreesPerSecond)
        orbit.setParent(parent)

        // Create the planet and position it relative to the sun.
        val planet = Planet(this, name, planetScale, renderable!!, solarSettings)
        planet.setParent(orbit)
        planet.localPosition = Vector3(auFromParent * AU_TO_METERS, 0.0f, 0.0f)

        return planet
    }

    private fun showLoadingMessage() {
        if (loadingMessageSnackbar != null && loadingMessageSnackbar!!.isShownOrQueued) {
            return
        }

        loadingMessageSnackbar = Snackbar.make(
            this@SolarActivity.findViewById(android.R.id.content),
            R.string.plane_finding,
            Snackbar.LENGTH_INDEFINITE)
        loadingMessageSnackbar!!.view.setBackgroundColor(-0x40cdcdce)
        loadingMessageSnackbar!!.show()
    }

    private fun hideLoadingMessage() {
        if (loadingMessageSnackbar == null) {
            return
        }

        loadingMessageSnackbar!!.dismiss()
        loadingMessageSnackbar = null
    }

    companion object {
        private val RC_PERMISSIONS = 0x123

        // Astronomical units to meters ratio. Used for positioning the planets of the solar system.
        private val AU_TO_METERS = 0.5f
    }
}
private infix fun com.google.ar.sceneform.Scene.setTo(scene: Scene) {
    this.addChild(scene.nodes.first())
}
