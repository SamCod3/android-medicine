package com.samcod3.meditrack.domain.util

import android.util.Log

/**
 * Utility class for extracting National Codes (CN) from various barcode formats.
 * Supports Spanish pharmaceutical barcodes (EAN-13, GTIN-14, GS1 DataMatrix).
 */
object BarcodeExtractor {
    
    private const val TAG = "BarcodeExtractor"
    
    /**
     * Extracts the National Code from a raw barcode string.
     * Handles EAN-13, GTIN-14, and GS1 DataMatrix formats.
     */
    fun extractNationalCode(barcode: String): String {
        // Clean up the barcode (remove FNC1 characters and whitespace)
        val cleanCode = barcode.replace("\u001D", "").replace("\\s".toRegex(), "")
        
        Log.d(TAG, "Clean code: $cleanCode (length: ${cleanCode.length})")
        
        // Check for GS1 DataMatrix format (starts with 01 for GTIN)
        if (cleanCode.startsWith("01") && cleanCode.length >= 16) {
            return extractFromGS1(cleanCode)
        }
        
        // 1. Check for specific Health prefixes (NTIN) where CN is embedded
        // Format: P(6) + CN(6) + DC(1)
        
        // Medicamentos (847000)
        if (cleanCode.startsWith("847000") && cleanCode.length == 13) {
            return cleanCode.substring(6, 12)
        }

        // Productos Sanitarios (848000)
        if (cleanCode.startsWith("848000") && cleanCode.length == 13) {
            return cleanCode.substring(6, 12)
        }

        // 2. Generic EAN-13 (Spain 84) fallback
        // WARNING: Commercial products (843, 841...) DO NOT have an embedded CN in fixed position.
        // We strictly avoid extracting substrings for them to prevent false CNs.
        
        // Check if it's already a plain national code (6-7 digits)
        if (cleanCode.length in 6..7 && cleanCode.all { it.isDigit() }) {
            return cleanCode.trimStart('0')
        }
        
        // Return the full code (GTIN/EAN) for everything else
        return cleanCode
    }
    
    /**
     * Extracts CN from GS1 DataMatrix / GTIN-14 format.
     */
    private fun extractFromGS1(code: String): String {
        Log.d(TAG, "Parsing GS1: $code")
        
        // AI 01 = GTIN (14 digits)
        val gtinStart = code.indexOf("01")
        if (gtinStart != -1 && code.length >= gtinStart + 16) {
            val gtin = code.substring(gtinStart + 2, gtinStart + 16)
            Log.d(TAG, "GTIN: $gtin")
            
            // Spanish NTIN (Medicines & Health Products)
            // 847000xxxxxxC -> Medicines
            // 848000xxxxxxC -> Health Products
            if (gtin.startsWith("0847000") || gtin.startsWith("847000") || 
                gtin.startsWith("0848000") || gtin.startsWith("848000")) {
                
                // Allow for 13 or 14 digit GTIN string inputs
                val offset = if (gtin.length == 14) 7 else 6 
                
                // Extract the next 6 digits (CN)
                if (gtin.length >= offset + 6) {
                    val cn = gtin.substring(offset, offset + 6)
                    Log.d(TAG, "NTIN detected. Extracted CN: $cn")
                    return cn
                }
            }
        }
        
        return code
    }
    
    /**
     * Finds National Code from OCR text.
     * Simplified to only look for explicit CN labels: "C.N. 123456" or "CN: 123456.7"
     */
    fun findCNInText(text: String): String? {
        Log.d(TAG, "Scanning OCR text for CN label")
        
        // Look for explicit CN label patterns:
        // - "C.N. 123456" or "C.N. 123456.7"
        // - "CN: 123456" or "CN 123456"
        // - "C.N: 123456.7"
        val cnLabelRegex = Regex(
            """(?:C\.?\s*N\.?|C\.N)\s*[:\.]?\s*(\d{6,7})(?:\.\d)?""", 
            RegexOption.IGNORE_CASE
        )
        
        val match = cnLabelRegex.find(text)
        if (match != null) {
            val cn = match.groupValues[1]
            Log.d(TAG, "Found CN from label: $cn")
            return cn
        }
        
        Log.d(TAG, "No CN label found in text")
        return null
    }
}
