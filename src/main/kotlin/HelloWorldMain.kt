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
        println("Starting Quarkus Command-Line Guitar Tuner...")

        // Setup Audio Format: 44.1kHz, 16-bit, Mono, Signed, Little-Endian
        val sampleRate = 44100f
        val format = AudioFormat(sampleRate, 16, 1, true, false)
        val info = DataLine.Info(TargetDataLine::class.java, format)

        if (!AudioSystem.isLineSupported(info)) {
            println("Error: Microphone is not supported or not found on this system.")
            return 1
        }

        val line = AudioSystem.getLine(info) as TargetDataLine
        line.open(format)
        line.start()

        val bufferSize = 4096
        val buffer = ByteArray(bufferSize)
        val floatBuffer = FloatArray(bufferSize / 2)

        println("Listening to microphone... Play a string! (Press Ctrl+C to stop)")
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
                    // Use \r to overwrite the same line in the Windows console
                    print("\r$tuningStatus")
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

        val advice = when {
            diff < -1.5f -> "Tune UP   (->)"
            diff > 1.5f  -> "Tune DOWN (<-)"
            else         -> "IN TUNE   (==)"
        }

        val padding = " ".repeat(10) // clears trailing console characters

        return "Pitch: %6.2f Hz | %-9s | Target: %6.2f Hz | %s %s%s".format(
            frequency, closestName, targetFrequency, visualizer, advice, padding
        )
    }

    private fun createVisualizer(diff: Float, inTune: Boolean): String {
        val maxVisualDiff = 5.0f // We visualize differences up to 5 Hz in either direction
        val halfWidth = 12       // Size of the scale on one side

        // Map the hz difference to our visual scale position
        val position = ((diff / maxVisualDiff) * halfWidth).toInt().coerceIn(-halfWidth, halfWidth)

        val builder = StringBuilder()
        builder.append("[")

        for (i in -halfWidth..halfWidth) {
            when {
                i == position -> {
                    // Output the marker using ANSI color codes
                    val markerColor = if (inTune) "\u001B[32m" else "\u001B[31m" // Green or Red
                    val resetColor = "\u001B[0m"
                    builder.append("${markerColor}O${resetColor}")
                }
                i == 0 -> builder.append("|") // Center target tick
                i == -halfWidth || i == halfWidth -> builder.append(":") // Edges of the sweet spot
                else -> builder.append("-")
            }
        }

        builder.append("]")
        return builder.toString()
    }
}