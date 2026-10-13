package br.dev.monitorremoto.ui

import com.journeyapps.barcodescanner.CaptureActivity

/** Activity local e não exportada para leitura de QR.
 * Usa o ciclo de câmera/permissões da biblioteca oficial ZXing Embedded.
 * O manifesto a registra explicitamente sem clearTaskOnLaunch da Activity genérica.
 */
class QrScanActivity : CaptureActivity()
