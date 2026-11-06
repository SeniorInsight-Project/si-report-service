package com.seniorinsight.report.adapters.out.armazenamento;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.time.Duration;

import com.seniorinsight.report.application.porta.saida.ArmazenamentoObjetos;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;

/**
 * {@link ArmazenamentoObjetos} sobre a API S3 do MinIO.
 *
 * <p>Usa dois clientes: um no endereço interno (upload, ex.: {@code http://minio:9000} no Compose) e outro no endereço
 * público, só para assinar as URLs entregues ao usuário. A assinatura é calculada localmente, sem chamada de rede.
 */
class ArmazenamentoMinio implements ArmazenamentoObjetos {

    private final MinioClient cliente;
    private final MinioClient clientePublico;
    private final String bucket;
    private volatile boolean bucketVerificado;

    ArmazenamentoMinio(MinioClient cliente, MinioClient clientePublico, String bucket) {
        this.cliente = cliente;
        this.clientePublico = clientePublico;
        this.bucket = bucket;
    }

    @Override
    public void salvar(String chave, byte[] conteudo, String contentType) {
        try {
            garantirBucket();
            cliente.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(chave)
                    .stream(new ByteArrayInputStream(conteudo), conteudo.length, -1)
                    .contentType(contentType)
                    .build());
        } catch (Exception e) {
            throw new ArmazenamentoException("Falha ao gravar o objeto " + chave, e);
        }
    }

    @Override
    public URI gerarUrlAssinada(String chave, Duration validade) {
        if (validade.isNegative() || validade.isZero() || validade.compareTo(VALIDADE_URL_ASSINADA) > 0) {
            throw new IllegalArgumentException("A validade da URL assinada deve estar entre 1s e 7 dias: " + validade);
        }
        try {
            return URI.create(clientePublico.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET)
                    .bucket(bucket)
                    .object(chave)
                    .expiry((int) validade.toSeconds())
                    .build()));
        } catch (Exception e) {
            throw new ArmazenamentoException("Falha ao assinar a URL do objeto " + chave, e);
        }
    }

    /** O bucket é criado no primeiro upload, para o serviço subir mesmo com o MinIO fora do ar. */
    private void garantirBucket() throws Exception {
        if (bucketVerificado) {
            return;
        }
        if (!cliente.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            cliente.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
        bucketVerificado = true;
    }

    static class ArmazenamentoException extends RuntimeException {

        ArmazenamentoException(String mensagem, Throwable causa) {
            super(mensagem, causa);
        }
    }
}
