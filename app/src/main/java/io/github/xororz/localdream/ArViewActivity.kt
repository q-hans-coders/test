package io.github.xororz.localdream

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.assets.RenderableSource
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode

class ArViewActivity : AppCompatActivity() {

    private var arFragment: ArFragment? = null
    private var placedAnchorNode: AnchorNode? = null
    private var placedTransformable: TransformableNode? = null
    private var modelUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar)

        // 모델 URL 받아오기
        modelUrl = intent.getStringExtra("model_url")
        if (modelUrl.isNullOrBlank()) {
            Toast.makeText(this, "3D 모델 URL이 없습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // ArFragment 초기화
        arFragment = supportFragmentManager
            .findFragmentById(R.id.arFragment) as? ArFragment
        if (arFragment == null) {
            Toast.makeText(this, "AR 프래그먼트를 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 첫 터치로 배치
        setPlaneTapListener()

        // FAB 초기화
        findViewById<FloatingActionButton>(R.id.fabReset).setOnClickListener {
            resetPlacement()
        }
        findViewById<FloatingActionButton>(R.id.fabDownload).setOnClickListener {
            downloadModel()
        }
        findViewById<FloatingActionButton>(R.id.fabRotateLeft).setOnClickListener {
            rotateModel(-30f)
        }
        findViewById<FloatingActionButton>(R.id.fabRotateRight).setOnClickListener {
            rotateModel(+30f)
        }
    }

    /** 평면 탭 → 모델 로드 & 배치 */
    private fun setPlaneTapListener() {
        arFragment?.setOnTapArPlaneListener { hit: HitResult, _: Plane, _ ->
            modelUrl?.let { url ->
                loadModelAndPlace(hit, url)
                arFragment?.setOnTapArPlaneListener(null)
            }
        }
    }

    private fun loadModelAndPlace(hit: HitResult, url: String) {
        val source = RenderableSource.builder()
            .setSource(this, Uri.parse(url), RenderableSource.SourceType.GLB)
            .setRecenterMode(RenderableSource.RecenterMode.ROOT)
            .build()

        ModelRenderable.builder()
            .setSource(this, source)
            .setRegistryId(url)
            .build()
            .thenAccept { renderable ->
                // 기존 배치가 있으면 해제
                placedAnchorNode?.anchor?.detach()
                placedAnchorNode?.let { arFragment?.arSceneView?.scene?.removeChild(it) }

                // AnchorNode 생성
                placedAnchorNode = AnchorNode(hit.createAnchor()).apply {
                    setParent(arFragment!!.arSceneView.scene)
                }

                // TransformableNode 생성 (핀치/드래그/회전 지원)
                placedTransformable = TransformableNode(arFragment!!.transformationSystem).apply {
                    setParent(placedAnchorNode)
                    this.renderable = renderable
                    localScale = Vector3(0.5f, 0.5f, 0.5f)
                    select()
                }
            }
            .exceptionally { t ->
                Toast.makeText(this, "모델 로드 실패: ${t.message}", Toast.LENGTH_LONG).show()
                null
            }
    }

    /** 배치된 모델 & 앵커 해제 */
    private fun resetPlacement() {
        placedTransformable?.let {
            placedAnchorNode?.removeChild(it)
            placedTransformable = null
        }
        placedAnchorNode?.let {
            it.anchor?.detach()
            arFragment?.arSceneView?.scene?.removeChild(it)
            placedAnchorNode = null
        }
        setPlaneTapListener()
        Toast.makeText(this, "배치를 초기화했습니다", Toast.LENGTH_SHORT).show()
    }

    /** DownloadManager로 GLB 파일 다운로드 */
    private fun downloadModel() {
        val url = modelUrl ?: return
        val req = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("3D 모델 다운로드")
            setDescription("다운로드 중…")
            setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                "model_${System.currentTimeMillis()}.glb"
            )
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }
        (getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
        Toast.makeText(this, "다운로드를 시작합니다", Toast.LENGTH_SHORT).show()
    }

    /** 노드를 Y축 기준으로 회전 */
    private fun rotateModel(angle: Float) {
        placedTransformable?.let {
            val rot = Quaternion.axisAngle(Vector3(0f, 1f, 0f), angle)
            it.localRotation = Quaternion.multiply(it.localRotation, rot)
        }
    }

    override fun onDestroy() {
        arFragment?.arSceneView?.destroy()
        super.onDestroy()
    }
}
