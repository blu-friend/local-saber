package com.localsaber.app

import android.os.Bundle
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import io.flutter.embedding.android.FlutterActivity
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.Intent
import android.content.ContentUris
import androidx.documentfile.provider.DocumentFile
import android.net.Uri
import android.provider.DocumentsContract
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity: FlutterActivity() {
    private val CHANNEL = "com.localsaber.app/storage"
    private val REQUEST_CODE_OPEN_DOCUMENT_TREE = 42
    private var pendingResult: MethodChannel.Result? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent.getIntExtra("org.chromium.chrome.extra.TASK_ID", -1) == this.taskId) {
            this.finish()
            intent.addFlags(FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightNavigationBars = true
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "requestSAF" -> requestStorageAccessFramework(result)
                    "requestSAFForFolder" -> {
                        val folderPath = call.argument<String>("folderPath")
                        if (folderPath != null) {
                            requestStorageAccessFrameworkForFolder(folderPath, result)
                        } else {
                            result.error("SAF_ERROR", "Folder path is required", null)
                        }
                    }
                    "initSAF" -> initStorageAccessFramework(result)
                    else -> result.notImplemented()
                }
            }
    }

    private fun requestStorageAccessFramework(result: MethodChannel.Result) {
        pendingResult = result
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT_TREE)
    }

    private fun initStorageAccessFramework(result: MethodChannel.Result) {
        // Trigger SAF folder selection on first run
        requestStorageAccessFramework(result)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_OPEN_DOCUMENT_TREE && resultCode == RESULT_OK) {
            val uri = data?.data ?: run {
                pendingResult?.error("SAF_ERROR", "No folder selected", null)
                pendingResult = null
                return
            }

            try {
                // Take persistent permissions
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )

                // Get the path from the URI
                val path = getPathFromUri(uri)
                pendingResult?.success(path)
            } catch (e: Exception) {
                pendingResult?.error("SAF_ERROR", "Error getting folder path: ${e.message}", null)
            }
            pendingResult = null
        }
    }

    private fun getPathFromUri(uri: Uri): String? {
        return when {
            // For DocumentFile (SAF)
            DocumentsContract.isDocumentUri(this, uri) -> {
                getDocumentPath(uri)
            }
            // For file:// URIs
            uri.scheme == "file" -> {
                uri.path
            }
            else -> {
                try {
                    // Try to resolve through DocumentFile
                    val docFile = DocumentFile.fromTreeUri(this, uri)
                    docFile?.uri?.toString()
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    private fun getDocumentPath(uri: Uri): String? {
        val docId = DocumentsContract.getDocumentId(uri)
        val split = docId.split(":")
        
        return try {
            when {
                // Handle primary external storage
                split.size == 2 && split[0] == "primary" -> {
                    "/storage/emulated/0/${split[1]}"
                }
                // For other storage locations
                else -> {
                    // Try to get path from DocumentFile
                    val docFile = DocumentFile.fromTreeUri(this, uri)
                    docFile?.uri?.toString() ?: docId
                }
            }
        } catch (e: Exception) {
            docId
        }
    }

    private fun requestStorageAccessFrameworkForFolder(
        folderPath: String,
        result: MethodChannel.Result,
    ) {
        try {
            // Parse the folder path to extract components
            // Path format: /storage/emulated/0/local-saber/
            val pathParts = folderPath.trim('/').split('/')
            
            // For paths like /storage/emulated/0/local-saber, construct the URI
            val uri = if (pathParts.size >= 3 && pathParts[0] == "storage" && pathParts[1] == "emulated") {
                // Reconstruct the tree URI for primary external storage
                val document = pathParts.drop(3).joinToString("%2F")
                Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A${if (document.isNotEmpty()) "$document" else ""}")
            } else {
                // Fallback: construct a generic tree URI
                Uri.parse("content://com.android.externalstorage.documents/tree/primary")
            }

            // Try to take persistent permissions for this folder
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                result.success(true)
                return
            } catch (e: Exception) {
                // If takePersistableUriPermission fails, open the picker
            }

            // Fallback: open document tree picker
            pendingResult = result
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                // Try to start at the default folder
                putExtra(
                    DocumentsContract.EXTRA_INITIAL_URI,
                    Uri.parse("content://com.android.externalstorage.documents/tree/primary%3Alocal-saber")
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT_TREE)
        } catch (e: Exception) {
            result.error("SAF_ERROR", "Error requesting SAF for folder: ${e.message}", null)
        }
    }
}
