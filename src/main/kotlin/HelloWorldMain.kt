import io.quarkus.runtime.QuarkusApplication
import io.quarkus.runtime.annotations.QuarkusMain
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.TargetDataLine
import kotlin.math.abs
import kotlin.math.sqrt

@QuarkusMain
class HelloWorldMain : QuarkusApplication {
    override fun run(vararg args: String?): Int {
        val ansiCyan = "\u001B[36m"
        val ansiReset = "\u001B[0m"
        val ansiBold = "\u001B[1m"

        println("$ansiCyan$ansiBold=== Modern CLI Guitar Tuner ===$ansiReset")

        // Setup Audio Format: 44.1kHz, 16-bit, Mono, Signed, Little-Endian
        val sampleRate = 44100f
        val format = AudioFormat(sampleRate, 16, 1, true, false)
        val info = DataLine.Info(TargetDataLine::class.java, format)

        if (!AudioSystem.isLineSupported(info)) {
            println("\u001B[31mError: Microphone is not supported or not found on this system.\u001B[0m")
            return 1
        }

        val line = AudioSystem.getLine(info) as TargetDataLine
        line.open(format)
        line.start()

        val bufferSize = 4096
        val buffer = ByteArray(bufferSize)
        val floatBuffer = FloatArray(bufferSize / 2)

        println("\u001B[90mListening to microphone... Play a string! (Press Ctrl+C to stop)\u001B[0m")
        println() // Blank line for cleaner UI separation

        val noiseThreshold = 0.001f

        // Print this once before the loop starts
        print("Waiting for a string pluck...\r")

        while (true) {
            val bytesRead = line.read(buffer, 0, buffer.size)
            if (bytesRead > 0) {
                var sumSquares = 0.0f

                // Convert 16-bit PCM bytes to normalized floats (-1.0 to 1.0)
                for (i in 0 until bytesRead / 2) {
                    val sample = (buffer[2 * i].toInt() and 0xFF) or (buffer[2 * i + 1].toInt() shl 8)
                    val floatSample = sample.toFloat() / 32768.0f
                    floatBuffer[i] = floatSample
                    sumSquares += floatSample * floatSample
                }

                // Calculate RMS (Root Mean Square) to measure the volume of this buffer
                val rms = sqrt(sumSquares / (bytesRead / 2))

                // If the audio is too quiet, skip pitch detection but DO NOT erase the screen
                if (rms < noiseThreshold) {
                    continue
                }

                val frequency = estimatePitch(floatBuffer, sampleRate)

                // Filter out noise and irrelevant frequencies
                if (frequency > 50f && frequency < 400f) {
                    val tuningStatus = getClosestGuitarString(frequency)
                    // Use \u001B[2K to clear the current line, then \r to overwrite
                    print("\u001B[2K\r$tuningStatus")
                }
            }
        }
    }

    private fun estimatePitch(buffer: FloatArray, sampleRate: Float): Float {
        var maxCorrelation = 0f
        var bestPeriod = -1

        // Autocorrelation parameters to detect frequencies roughly between 60 Hz and 1000 Hz
        val minPeriod = (sampleRate / 1000f).toInt()
        val maxPeriod = (sampleRate / 60f).toInt()

        for (period in minPeriod until maxPeriod) {
            var correlation = 0f
            for (i in 0 until buffer.size - period) {
                correlation += buffer[i] * buffer[i + period]
            }
            if (correlation > maxCorrelation) {
                maxCorrelation = correlation
                bestPeriod = period
            }
        }

        return if (bestPeriod != -1) sampleRate / bestPeriod else 0f
    }

    private fun getClosestGuitarString(frequency: Float): String {
        val strings = mapOf(
            "E2 (Low)" to 82.41f,
            "A2"       to 110.00f,
            "D3"       to 146.83f,
            "G3"       to 196.00f,
            "B3"       to 246.94f,
            "E4 (High)" to 329.63f
        )

        var closestName = "Unknown"
        var minDiff = Float.MAX_VALUE
        var targetFrequency = 0f

        for ((name, targetFreq) in strings) {
            val diff = abs(frequency - targetFreq)
            if (diff < minDiff) {
                minDiff = diff
                closestName = name
                targetFrequency = targetFreq
            }
        }

        val diff = frequency - targetFrequency
        val inTune = abs(diff) <= 1.5f // Anything within 1.5 Hz is considered "in tune"

        val visualizer = createVisualizer(diff, inTune)

        val reset = "\u001B[0m"
        val adviceColor = if (inTune) "\u001B[1;32m" else if (abs(diff) <= 5.0f) "\u001B[1;33m" else "\u001B[1;31m"

        val advice = when {
            diff < -1.5f -> "$adviceColor TUNE UP   »$reset"
            diff > 1.5f  -> "$adviceColor« TUNE DOWN  $reset"
            else         -> "$adviceColor ✓ PERFECT    $reset"
        }

        // We do the basic string formatting first to prevent ANSI codes from breaking the layout width
        val textInfo = "Pitch: %6.2f Hz  │  Note: %-9s │  Target: %6.2f Hz".format(
            frequency, closestName, targetFrequency
        )

        return "$textInfo  │  $visualizer  │  $advice"
    }

    private fun createVisualizer(diff: Float, inTune: Boolean): String {
        val maxVisualDiff = 5.0f // We visualize differences up to 5 Hz in either direction
        val halfWidth = 15       // Wider for a more modern, smooth gauge

        // Map the hz difference to our visual scale position
        val position = ((diff / maxVisualDiff) * halfWidth).toInt().coerceIn(-halfWidth, halfWidth)

        val builder = StringBuilder()
        val reset = "\u001B[0m"
        val dim = "\u001B[90m"
        val brightWhite = "\u001B[1;37m"

        builder.append("$dim[$reset ")

        for (i in -halfWidth..halfWidth) {
            when {
                i == position -> {
                    // Marker changes color based on proximity to center
                    val markerColor = if (inTune) "\u001B[1;32m" else if (abs(i) <= halfWidth / 2) "\u001B[1;33m" else "\u001B[1;31m"
                    builder.append("${markerColor}┼${reset}")
                }
                i == 0 -> builder.append("${brightWhite}┼${reset}") // Center target tick
                else -> builder.append("${dim}─${reset}")
            }
        }

        builder.append(" $dim]$reset")
        return builder.toString()
    }
}