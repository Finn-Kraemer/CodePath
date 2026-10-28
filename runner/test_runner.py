"""Angriffe auf die Sandbox. Läuft in der CI im Runner-Image:  python -m unittest test_runner"""
import unittest

import runner


class SandboxTest(unittest.TestCase):

    def check(self, code, status, stdin=""):
        result = runner.run(code, stdin)
        self.assertEqual(status, result["status"], {k: str(v)[:300] for k, v in result.items()})
        return result

    def test_hello(self):
        self.assertEqual("Hallo Welt\n", self.check("print('Hallo', input())", "OK", "Welt\n")["stdout"])

    def test_error(self):
        self.assertIn("ZeroDivisionError", self.check("print(1/0)", "ERROR")["stderr"])

    def test_no_network(self):
        self.check("import socket; socket.create_connection(('1.1.1.1', 80), timeout=2)", "ERROR")

    def test_no_secrets(self):
        self.check("open('/etc/passwd').read()", "ERROR")
        self.check("import os; os.listdir('/app')", "ERROR")
        # Das Token und andere Variablen des Dienstes kommen nicht in der Sandbox an
        self.assertEqual("False\n", self.check("import os; print('RUNNER_TOKEN' in os.environ)", "OK")["stdout"])

    def test_read_only_usr(self):
        self.check("open('/usr/x', 'w').write('x')", "ERROR")

    def test_fork_bomb(self):
        self.check("import os\nwhile True:\n    os.fork()", "ERROR")

    def test_memory(self):
        self.assertIn("MemoryError", self.check("x = 'a' * (10**9)", "ERROR")["stderr"])

    def test_cpu(self):
        self.check("while True: pass", "TIMEOUT")

    def test_sleep(self):
        self.check("import time; time.sleep(30)", "TIMEOUT")

    def test_output_flood(self):
        result = self.check("while True: print('x' * 1000)", "ERROR")
        self.assertLessEqual(len(result["stdout"]), runner.OUTPUT_BYTES)

    def test_big_file(self):
        self.check("open('/tmp/f', 'w').write('x' * 10**8)", "ERROR")

    def test_runaway_process_can_be_killed(self):
        self.assertEqual("TIMEOUT", runner.execute("import time; time.sleep(30)", "", runner.BASE_UID, wall_seconds=1)["status"])

    def test_health(self):
        self.assertTrue(runner.healthy())


if __name__ == "__main__":
    unittest.main()
