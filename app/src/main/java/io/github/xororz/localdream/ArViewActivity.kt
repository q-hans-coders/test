package io.github.xororz.localdream

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.ux.ArFragment
import java.io.File
import android.graphics.BitmapFactory
import android.view.ViewGroup
import android.widget.ImageView
import com.google.ar.sceneform.math.Vector3

class ArViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar)

        val uri = intent.getStringExtra("image_uri")?.let { Uri.parse(it) }
        if (uri == null) {
            Toast.makeText(this, "이미지 URI가 없습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val bmp = contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: run {
            Toast.makeText(this, "이미지를 열 수 없습니다", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val arFragment = supportFragmentManager
            .findFragmentById(R.id.arFragment) as ArFragment

        // 평면을 탭했을 때 이미지 띄우기
        arFragment.setOnTapArPlaneListener { hitResult: HitResult, plane: Plane, _ ->
            // 1) 이미지를 Anchor에 붙이기 위해 미리 ImageView 생성
            val imageView = ImageView(this).apply {
                layoutParams = ViewGroup.LayoutParams(600, 600)
                setImageBitmap(bmp)
            }

            // 2) ViewRenderable 생성: setView(Context, View) 사용
            ViewRenderable.builder()
                .setView(this, imageView)      // ← 여기 수정
                .build()
                .thenAccept { viewRenderable: ViewRenderable ->   // ← 타입 명시
                    // 3) AnchorNode + Node 로 씬에 추가
                    val anchor = hitResult.createAnchor()
                    val anchorNode = AnchorNode(anchor).apply {
                        setParent(arFragment.arSceneView.scene)
                    }
                    Node().apply {
                        setParent(anchorNode)
                        renderable = viewRenderable
                        localScale = Vector3(0.3f, 0.3f, 0.3f)
                    }
                }
                .exceptionally { throwable ->
                    Toast.makeText(this, "AR 뷰 로드 실패: ${throwable.message}", Toast.LENGTH_SHORT)
                        .show()
                    null
                }
        }
    }
}