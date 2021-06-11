package com.example.cameragalleryimagepicker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class ProfileActivity : AppCompatActivity() {

    private val MY_CAMERA_PERMISSION_CODE = 100
    private val IMAGE_PERMISSION_CODE = 105
    private var imageView: ImageView? = null
    private var profilePicutre: ImageView? = null
    private var pathToFile: String? = null

    val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        imageView = findViewById(R.id.imagepic)
        profilePicutre = findViewById(R.id.profilepic)
        loadProfilePic(stringToBitmap(PreferenceHelp.getimage()))
        clickListeners()
        checkPersmission()
        statusBarTransparent()
    }

    private fun clickListeners() {
        profilePicutre?.setOnClickListener {
            showDialog()
        }
    }

    private fun checkPersmission() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermission()
        }
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, permissions, 0)
    }

    private fun statusBarTransparent() {
        if (Build.VERSION.SDK_INT >= 24) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            val window = window
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            window.statusBarColor = Color.TRANSPARENT
        }
    }

    private fun showDialog() {
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        bottomSheetDialog.setCancelable(true)
        bottomSheetDialog.setContentView(R.layout.profilepopup)
        val gallery = bottomSheetDialog.findViewById<ImageView>(R.id.gallery)
        val camera = bottomSheetDialog.findViewById<ImageView>(R.id.camera)
        gallery?.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                val permissions = arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                requestPermissions(permissions, IMAGE_PERMISSION_CODE)
            } else {
                chooseImageGallery()
            }
            bottomSheetDialog.dismiss()
        }

        camera?.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermission()
            } else {
                captureImageFromCamera()
            }
            bottomSheetDialog.dismiss()
        }
        bottomSheetDialog.show()
    }

    private var onActivityResultLauncherGallery =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val selectedImage = result.data?.data
            val databaseUri: Uri?
            val selection: String?
            val selectionArgs: Array<String>?
            if (selectedImage?.path?.contains("/document/image:") == true) {
                databaseUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                selection = "_id=?"
                selectionArgs =
                    arrayOf(DocumentsContract.getDocumentId(selectedImage).split(":")[1])
            } else {
                databaseUri = selectedImage
                selection = null
                selectionArgs = null
            }
            val column = "_data"
            val projection = arrayOf(column)
            val c = databaseUri?.let {
                contentResolver.query(
                    it,
                    projection,
                    selection,
                    selectionArgs,
                    null
                )
            }
            c?.moveToFirst()
            val columnIndex = c?.getColumnIndex(column)
            val picturePath = columnIndex?.let { c.getString(it) }
            c?.close()
            val thumbnail = BitmapFactory.decodeFile(picturePath)
            encodeImage(thumbnail)?.let { PreferenceHelp.setimage(it) }
            imageView?.let {
                Glide.with(this)
                    .load(thumbnail)
                    .circleCrop()
                    .placeholder(R.drawable.default_profile_pic)
                    .into(it)
            }
        }

    private fun chooseImageGallery() {
        val intent =
            Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        onActivityResultLauncherGallery.launch(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MY_CAMERA_PERMISSION_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show()
                captureImageFromCamera()
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show()
            }
        }
        when (requestCode) {
            IMAGE_PERMISSION_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    chooseImageGallery()
                } else {
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private var onActivityResultLauncherCamera =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val bitmap: Bitmap = BitmapFactory.decodeFile(pathToFile)
            encodeImage(bitmap)?.let { PreferenceHelp.setimage(it) }
            imageView?.let {
                Glide.with(this)
                    .load(bitmap)
                    .circleCrop()
                    .placeholder(R.drawable.default_profile_pic)
                    .into(it)
            }
        }

    private fun captureImageFromCamera() {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (cameraIntent.resolveActivity(packageManager) != null) {
            val photoFile: File? = createPhotoFile()
            if (photoFile != null) {
                pathToFile = photoFile.absolutePath
                val photoUri = FileProvider.getUriForFile(
                    this,
                    "com.example.cameragalleryimagepicker.fileprovider",
                    photoFile
                )
                Log.d("uri", "PhotoUri is $photoUri")
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                onActivityResultLauncherCamera.launch(cameraIntent)
            }
        }
    }

    private fun createPhotoFile(): File? {
        val savingTime = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDirectory: File? = MyApplication.getApplicationContext()
            .getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        var imageTaken: File? = null
        try {
            imageTaken = File.createTempFile(savingTime, ".jpg", storageDirectory)
            storageDirectory?.mkdir()
        } catch (ioE: IOException) {
            Log.d("myLog", "Exe: $ioE")
        }
        return imageTaken
    }

    private fun loadProfilePic(profilePic: Bitmap?) {
        imageView?.let {
            Glide.with(this)
                .load(profilePic)
                .circleCrop()
                .placeholder(R.drawable.default_profile_pic)
                .into(it)
        }
    }

    private fun encodeImage(bm: Bitmap): String? {
        val bAOS = ByteArrayOutputStream()
        bm.compress(Bitmap.CompressFormat.JPEG, 50, bAOS)
        val b = bAOS.toByteArray()
        val encImage: String? = Base64.encodeToString(b, Base64.DEFAULT)
        return encImage
    }

    private fun stringToBitmap(stringUrl: String?): Bitmap? {
        val imageBytes = Base64.decode(stringUrl, 0)
        val image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        return image
    }

}







