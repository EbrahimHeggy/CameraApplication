package dev.ebrahim.cameraapplication

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.room.Room
import coil.compose.AsyncImage
import dev.ebrahim.cameraapplication.databsae.AppDatabase
import dev.ebrahim.cameraapplication.databsae.ImageDao
import dev.ebrahim.cameraapplication.databsae.ImageEntity
import dev.ebrahim.cameraapplication.ui.theme.CameraApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private var shouldShowCamera: MutableState<Boolean> = mutableStateOf(false)
    private lateinit var photoUri: Uri
    private var shouldShowPhoto: MutableState<Boolean> = mutableStateOf(false)
    private lateinit var imageDao: ImageDao


    override fun onCreate(savedInstanceState: Bundle?) {
        cameraExecutor = Executors.newSingleThreadExecutor()
        outputDirectory = getOutputDirectory()
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "image-database"
        ).build()

        super.onCreate(savedInstanceState)
        setContent {
            CameraApplicationTheme {
                val context = LocalContext.current
                imageDao = db.imageDao()
                val images: List<ImageEntity> by imageDao.getAllImages().collectAsState(initial = emptyList())


                var selectedImageUris by remember {
                    mutableStateOf<List<Uri>>(emptyList())
                }

                val multiplePhotoPickerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.PickMultipleVisualMedia(),
                    onResult = { uris -> selectedImageUris = uris }
                )

                val requestPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        when {
                            isGranted -> {
                                Toast.makeText(context, "Permission Granted", Toast.LENGTH_SHORT)
                                    .show()
                                multiplePhotoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }

                            shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> {
                                Toast.makeText(context, "Permission Needed", Toast.LENGTH_SHORT)
                                    .show()
                            }

                            else -> {
                                showPermissionDeniedDialog(context)
                            }
                        }
                    }
                )

                val requestPermissionLauncherCam = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                    onResult = { isGranted ->
                        when {
                            isGranted -> {
                                Toast.makeText(
                                    context,
                                    "Cam Permission Granted",
                                    Toast.LENGTH_SHORT
                                ).show()
                                shouldShowCamera.value = true
                            }

                            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                                Toast.makeText(context, "Permission Needed", Toast.LENGTH_SHORT)
                                    .show()
                            }

                            else -> {
                                showPermissionDeniedDialog(context)
                            }
                        }
                    }
                )

                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                        ) {
//                            Button(onClick = {
//                                requestPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
//                            }) {
//                                Text(text = "Pick One photo or More")
//                            }
                        IconButton( modifier = Modifier
                            .padding(end = 16.dp),
                            onClick = {
                            requestPermissionLauncherCam.launch(Manifest.permission.CAMERA)
                        }) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color.Black
                            )
                        }

                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                    ) {
//                        if (shouldShowPhoto.value) {
//                            AsyncImage(model = photoUri, contentDescription = "null",
//                            modifier = Modifier.fillMaxWidth(),
//                            contentScale = ContentScale.Crop
//                            )
//                        }

                        items(images) { imageEntity ->
                            AsyncImage(
                                model = Uri.parse(imageEntity.uri),
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }

                }
                if (shouldShowCamera.value) {
                    CameraView(
                        executor = cameraExecutor,
                        outputDirectory = outputDirectory,
                        onImageCaptured = ::handleImageCapture,
                        onError = { Log.e("CamFail", "View error:", it) }
                    )
                }
            }
        }
    }

    private fun showPermissionDeniedDialog(context: Context) {
        val builder = AlertDialog.Builder(context)
        builder.apply {
            setTitle("Permission Denied")
            setMessage("To use this feature, you need to allow access to the gallery in the app settings.")
            setPositiveButton("Go to Settings") { _, _ ->
                openAppSettings(context)
            }
            setNegativeButton("Cancel", null)
            create().show()
        }
    }

    private fun openAppSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", context.packageName, null)
        intent.data = uri
        context.startActivity(intent)
    }

    private fun handleImageCapture(uri: Uri) {
        shouldShowCamera.value = false
        photoUri = uri
        shouldShowPhoto.value = true

        val imageEntity = ImageEntity(uri = uri.toString(), captureTime = System.currentTimeMillis())
        GlobalScope.launch(Dispatchers.IO) {
            imageDao.insertImage(imageEntity)
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }

        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
