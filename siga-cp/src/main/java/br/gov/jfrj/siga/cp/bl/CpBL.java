/*******************************************************************************
 * Copyright (c) 2006 - 2011 SJRJ.
 * 
 *     This file is part of SIGA.
 * 
 *     SIGA is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     SIGA is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with SIGA.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package br.gov.jfrj.siga.cp.bl;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang.StringUtils;

import br.gov.jfrj.siga.base.AplicacaoException;
import br.gov.jfrj.siga.base.Correio;
import br.gov.jfrj.siga.base.GeraMessageDigest;
import br.gov.jfrj.siga.base.Prop;
import br.gov.jfrj.siga.base.SigaCalendar;
import br.gov.jfrj.siga.base.SigaMessages;
import br.gov.jfrj.siga.base.Texto;
import br.gov.jfrj.siga.base.util.CPFUtils;
import br.gov.jfrj.siga.cp.AbstractCpAcesso.CpTipoAcessoEnum;
import br.gov.jfrj.siga.cp.CpAcesso;
import br.gov.jfrj.siga.cp.CpConfiguracao;
import br.gov.jfrj.siga.cp.CpIdentidade;
import br.gov.jfrj.siga.cp.CpMarcadorCorEnum;
import br.gov.jfrj.siga.cp.CpMarcadorIconeEnum;
import br.gov.jfrj.siga.cp.CpMarcadorTipoAplicacaoEnum;
import br.gov.jfrj.siga.cp.CpMarcadorTipoDataEnum;
import br.gov.jfrj.siga.cp.CpMarcadorTipoExibicaoEnum;
import br.gov.jfrj.siga.cp.CpMarcadorTipoInteressadoEnum;
import br.gov.jfrj.siga.cp.CpMarcadorTipoTextoEnum;
import br.gov.jfrj.siga.cp.CpModelo;
import br.gov.jfrj.siga.cp.CpPerfil;
import br.gov.jfrj.siga.cp.CpServico;
import br.gov.jfrj.siga.cp.CpSituacaoConfiguracao;
import br.gov.jfrj.siga.cp.CpTipoConfiguracao;
import br.gov.jfrj.siga.cp.CpTipoIdentidade;
import br.gov.jfrj.siga.cp.CpTipoMarcadorEnum;
import br.gov.jfrj.siga.cp.CpToken;
import br.gov.jfrj.siga.cp.converter.IEnumWithId;
import br.gov.jfrj.siga.cp.util.Excel;
import br.gov.jfrj.siga.cp.util.MatriculaUtils;
import br.gov.jfrj.siga.cp.util.SigaUtil;
import br.gov.jfrj.siga.dp.CpMarcador;
import br.gov.jfrj.siga.dp.CpOrgaoUsuario;
import br.gov.jfrj.siga.dp.DpCargo;
import br.gov.jfrj.siga.dp.DpFuncaoConfianca;
import br.gov.jfrj.siga.dp.DpLotacao;
import br.gov.jfrj.siga.dp.DpPessoa;
import br.gov.jfrj.siga.dp.dao.CpDao;
import br.gov.jfrj.siga.dp.dao.DpPessoaDaoFiltro;
import br.gov.jfrj.siga.gi.integracao.IntegracaoLdapViaWebService;
import br.gov.jfrj.siga.gi.service.GiService;
import br.gov.jfrj.siga.model.dao.ModeloDao;

public class CpBL {
	CpCompetenciaBL comp;

	public CpCompetenciaBL getComp() {
		return comp;
	}

	public void setComp(CpCompetenciaBL comp) {
		this.comp = comp;
	}

	private CpDao dao() {
		return comp.getConfiguracaoBL().dao();
	}

	/**
	 * Marca uma {@link CpConfiguracao} como Fechada. Para isso preenche a
	 * {@link CpConfiguracao#setHisDtFim(Date) data de fim}. <br/>
	 * <b>NOTA DE IMPLMENTAÇÃO:</b> Originalmente simplesmente chamava-se
	 * {@link CpDao#gravarComHistorico(br.gov.jfrj.siga.cp.model.HistoricoAuditavel, CpIdentidade)}
	 * com <code>confoOld</code> que é carregado da base de dados. Entretanto na
	 * migração para o EAP 7.2 e o JPA (ao invés do Hibernate) ao se executar
	 * <code>gravarComHistorico</code> era disparada uma exceção com a mensagem de
	 * erro <cite>detached entity passed to persist</cite>. A solução encontrada foi
	 * carregar uma outra {@link CpConfiguracao} da base de dados com o ID de
	 * <code>confoOld</code>, preecher a {@link CpConfiguracao#setHisDtFim(Date)
	 * data de fim} deste e passá-lo para <code>gravarComHistorico</code>.
	 * 
	 * @param confOld               Configuração a ser fechada.
	 * @param identidadeCadastrante Usuário responsável pela atualização
	 * @param dt                    Data de fechamenbto.
	 * @see CpDao#gravarComHistorico(br.gov.jfrj.siga.cp.model.HistoricoAuditavel,
	 *      CpIdentidade)
	 */
	private void fecharAntigaConfiguracao(CpConfiguracao confOld, CpIdentidade identidadeCadastrante, Date dt) {
		if (confOld != null) {
			Long idConfiguracaoOld = confOld.getIdConfiguracao();
			CpConfiguracao confOldToSave = dao().consultar(idConfiguracaoOld, CpConfiguracao.class, false);
			confOldToSave.setHisDtFim(dt);
			dao().gravarComHistorico(confOldToSave, identidadeCadastrante);
		}
	}

	public CpIdentidade alterarIdentidade(CpIdentidade ident, Date dtExpiracao, CpIdentidade identidadeCadastrante)
			throws AplicacaoException {
		try {
			Date dt = dao().consultarDataEHoraDoServidor();
			CpIdentidade idNova = new CpIdentidade();
			try {
				PropertyUtils.copyProperties(idNova, ident);
			} catch (Exception e) {
				throw new AplicacaoException("Erro ao copiar as propriedades da identidade anterior.");
			}
			idNova.setIdIdentidade(null);
			idNova.setDtExpiracaoIdentidade(dtExpiracao);

			dao().iniciarTransacao();
			CpIdentidade id = (CpIdentidade) dao().gravarComHistorico(idNova, ident, dt, identidadeCadastrante);
			dao().commitTransacao();
			return id;
		} catch (Exception e) {
			dao().rollbackTransacao();
			throw new AplicacaoException("Não foi possível cancelar a identidade.", 9, e);
		}
	}

	public void cancelarIdentidade(CpIdentidade ident, CpIdentidade identidadeCadastrante) throws AplicacaoException {
		try {
			Date dt = dao().consultarDataEHoraDoServidor();
			CpIdentidade idNova = new CpIdentidade();
			try {
				PropertyUtils.copyProperties(idNova, ident);
			} catch (Exception e) {
				throw new AplicacaoException("Erro ao copiar as propriedades da identidade anterior.");
			}
			idNova.setIdIdentidade(null);
			idNova.setDtCancelamentoIdentidade(dt);
			idNova.setHisDtFim(dt);
			dao().iniciarTransacao();
			dao().gravarComHistorico(idNova, ident, dt, identidadeCadastrante);
			dao().commitTransacao();
		} catch (Exception e) {
			dao().rollbackTransacao();
			throw new AplicacaoException("Não foi possível cancelar a identidade.", 9, e);
		}
	}

	public void bloquearIdentidade(CpIdentidade ident, CpIdentidade identidadeCadastrante, boolean fBloquear)
			throws Exception {
		CpTipoConfiguracao tpConf = dao().consultar(CpTipoConfiguracao.TIPO_CONFIG_FAZER_LOGIN,
				CpTipoConfiguracao.class, false);
		Date dt = dao().consultarDataEHoraDoServidor();

		CpConfiguracao confOld = null;
		try {
			CpConfiguracao confFiltro = new CpConfiguracao();
			confFiltro.setCpIdentidade(ident);
			confFiltro.setCpTipoConfiguracao(tpConf);
			confOld = comp.getConfiguracaoBL().buscaConfiguracao(confFiltro, new int[] { 0 }, null);
			if (confOld.getCpIdentidade() == null)
				confOld = null;
		} catch (Exception e) {
			e.printStackTrace();
		}

		CpConfiguracao conf = new CpConfiguracao();
		conf.setCpIdentidade(ident);
		conf.setCpSituacaoConfiguracao(dao().consultar(
				fBloquear ? CpSituacaoConfiguracao.SITUACAO_NAO_PODE
						: CpSituacaoConfiguracao.SITUACAO_IGNORAR_CONFIGURACAO_ANTERIOR,
				CpSituacaoConfiguracao.class, false));
		conf.setCpTipoConfiguracao(tpConf);
		conf.setHisDtIni(dt);

		dao().iniciarTransacao();
		fecharAntigaConfiguracao(confOld, identidadeCadastrante, dt);
		dao().gravarComHistorico(conf, identidadeCadastrante);
		dao().commitTransacao();
		comp.getConfiguracaoBL().limparCacheSeNecessario();
	}

	public void bloquearPessoa(DpPessoa pes, CpIdentidade identidadeCadastrante, boolean fBloquear) throws Exception {

		try {
			dao().iniciarTransacao();

			CpTipoConfiguracao tpConf = dao().consultar(CpTipoConfiguracao.TIPO_CONFIG_FAZER_LOGIN,
					CpTipoConfiguracao.class, false);
			Date dt = dao().consultarDataEHoraDoServidor();

			CpConfiguracao confOld = null;
			try {
				CpConfiguracao confFiltro = new CpConfiguracao();
				confFiltro.setDpPessoa(pes);
				confFiltro.setCpTipoConfiguracao(tpConf);
				confOld = comp.getConfiguracaoBL().buscaConfiguracao(confFiltro, new int[] { 0 }, null);
			} catch (Exception e) {
				e.printStackTrace();
			}

			CpConfiguracao conf = new CpConfiguracao();
			conf.setDpPessoa(pes);
			conf.setCpSituacaoConfiguracao(dao().consultar(
					fBloquear ? CpSituacaoConfiguracao.SITUACAO_NAO_PODE
							: CpSituacaoConfiguracao.SITUACAO_IGNORAR_CONFIGURACAO_ANTERIOR,
					CpSituacaoConfiguracao.class, false));
			conf.setCpTipoConfiguracao(tpConf);
			conf.setHisDtIni(dt);

			fecharAntigaConfiguracao(confOld, identidadeCadastrante, dt);
			dao().gravarComHistorico(conf, identidadeCadastrante);

			for (CpIdentidade ident : dao().consultaIdentidades(pes)) {
				if (fBloquear && ident.isBloqueada() == false)
					bloquearIdentidade(ident, identidadeCadastrante, true);
			}
			comp.getConfiguracaoBL().limparCacheSeNecessario();
			dao().commitTransacao();
		} catch (Exception e) {
			dao().rollbackTransacao();
			throw e;
		}
	}

	public CpConfiguracao configurarAcesso(CpPerfil perfil, CpOrgaoUsuario orgao, DpLotacao lotacao, DpPessoa pes,
			CpServico servico, CpSituacaoConfiguracao situacao, CpTipoConfiguracao tpConf,
			CpIdentidade identidadeCadastrante) throws Exception {
		Date dt = dao().consultarDataEHoraDoServidor();

		CpConfiguracao confOld = null;
		try {
			CpConfiguracao confFiltro = new CpConfiguracao();
			confFiltro.setCpGrupo(perfil);
			confFiltro.setDpPessoa(pes);
			confFiltro.setLotacao(lotacao);
			confFiltro.setOrgaoUsuario(orgao);
			confFiltro.setCpServico(servico);
			confFiltro.setCpTipoConfiguracao(tpConf);
			confOld = comp.getConfiguracaoBL().buscaConfiguracao(confFiltro, new int[] { 0 }, null);
			if (confOld != null && !confOld.isEspecifica(confFiltro))
				confOld = null;
			if (confOld != null) 
				confOld = dao().consultar(confOld.getIdConfiguracao(), CpConfiguracao.class, false);
		} catch (Exception e) {
			e.printStackTrace();
		}

		CpConfiguracao conf = new CpConfiguracao();
		conf.setCpGrupo(perfil);
		conf.setDpPessoa(pes);
		conf.setLotacao(lotacao);
		conf.setOrgaoUsuario(orgao);
		conf.setCpServico(servico);
		conf.setCpSituacaoConfiguracao(situacao);
		conf.setCpTipoConfiguracao(tpConf);
		conf.setHisDtIni(dt);

		fecharAntigaConfiguracao(confOld, identidadeCadastrante, dt);
		dao().gravarComHistorico(conf, identidadeCadastrante);

		comp.getConfiguracaoBL().limparCacheSeNecessario();

		return conf;
	}

	public String alterarSenha(String cpf, String email, String matricula) throws AplicacaoException {

		String resultado = "";
		try {
			if (Pattern.matches("\\d+", cpf) && cpf.length() == 11) {
				List<CpIdentidade> lista = null;

				if (matricula != null) {
					final long longmatricula = MatriculaUtils.getParteNumericaDaMatricula(matricula);
					DpPessoa pessoa = dao().consultarPorCpfMatricula(Long.parseLong(cpf), longmatricula);
					lista = dao().consultaIdentidades(pessoa);
				}

				if (email != null) {
					lista = dao().consultaIdentidadesPorCpfEmail(cpf, email);
				}

				if (!lista.isEmpty()) {
					lista = dao().consultaIdentidadesPorCpf(cpf);
				}
				if (!lista.isEmpty()) {
					String[] senhaGerada = new String[1];
					senhaGerada[0] = GeraMessageDigest.geraSenha();
					for (CpIdentidade cpIdentidade : lista) {
						Cp.getInstance().getBL().alterarSenhaDeIdentidade(cpIdentidade.getNmLoginIdentidade(),
								StringUtils.leftPad(cpIdentidade.getDpPessoa().getPessoaAtual().getCpfPessoa().toString(), 11, "0"),
								null, senhaGerada);
					}
					resultado = "OK";
				} else {
					resultado = "Usuário não localizado.";
				}
			} else {
				resultado = "Usuário não localizado.";
			}

		} catch (AplicacaoException e) {
			resultado = e.getMessage();
			e.printStackTrace();
		}
		return resultado;
	}

	/**
	 * Altera a senha da identidade.
	 * 
	 * @param matricula
	 * @param cpf
	 * @param idCadastrante
	 * @param senhaGerada   - Usado para retornar a senha gerada. É um array para
	 *                      que o valor seja passado como referência e o método que
	 *                      o chama tenha a oportunidade de conhecer a senha)
	 * @return
	 * @throws AplicacaoException
	 */
	public CpIdentidade alterarSenhaDeIdentidade(String matricula, String cpf, CpIdentidade idCadastrante,
			String[] senhaGerada) throws AplicacaoException {

		Long longCpf = CPFUtils.getLongValueValidaSimples(cpf);
		final List<DpPessoa> listaPessoas = dao().listarPorCpf(longCpf);
		if (listaPessoas.isEmpty()) {
			throw new AplicacaoException("O CPF informado está incorreto, tente novamente!");
		}

		final long longmatricula = MatriculaUtils.getParteNumericaDaMatricula(matricula);
		final DpPessoa pessoa = dao().consultarPorCpfMatricula(Long.parseLong(cpf), longmatricula);

		if (pessoa != null && pessoa.getSigla().equals(matricula) && pessoa.getEmailPessoaAtual() != null) {

			// final Usuario usuario =
			// dao().consultaUsuarioCadastrante(matricula);

			CpIdentidade id = dao().consultaIdentidadeCadastrante(matricula, true);
			if (id != null) {
				String novaSenha = GeraMessageDigest.geraSenha();
				if (senhaGerada[0] != null) {
					novaSenha = senhaGerada[0];
				}
				try {
					Date dt = dao().consultarDataEHoraDoServidor();
					CpIdentidade idNova = new CpIdentidade();
					PropertyUtils.copyProperties(idNova, id);
					idNova.setIdIdentidade(null);
					idNova.setDtCancelamentoIdentidade(null);
					idNova.setDtCriacaoIdentidade(dt);

					final String hashNova = GeraMessageDigest.executaHash(novaSenha.getBytes(), "MD5");
					idNova.setDscSenhaIdentidade(hashNova);

					idNova.setDscSenhaIdentidadeCripto(null);
					idNova.setDscSenhaIdentidadeCriptoSinc(null);

					dao().iniciarTransacao();
					dao().gravarComHistorico(idNova, id, dt, idCadastrante);
					dao().commitTransacao();

					if (SigaMessages.isSigaSP()) {
						String[] destinanarios = { pessoa.getEmailPessoaAtual() };
						Correio.enviar(null, destinanarios,
								"Esqueci Minha Senha", "",
								"<table>" + "<tbody>" + "<tr>"
										+ "<td style='height: 80px; background-color: #f6f5f6; padding: 10px 20px;'>"
										+ "<img style='padding: 10px 0px; text-align: center;' src='http://www.documentos.spsempapel.sp.gov.br/siga/imagens/logo-sem-papel-cor.png' "
										+ "alt='SP Sem Papel' width='108' height='50' /></td>" + "</tr>" + "<tr>"
										+ "<td style='background-color: #bbb; padding: 0 20px;'>"
										+ "<h3 style='height: 20px;'>Governo do Estado de S&atilde;o Paulo</h3>"
										+ "</td>" + "</tr>" + "<tr style='height: 310px;'>"
										+ "<td style='height: 310px; padding: 10px 20px;'>" + "<div>"
										+ "<h4><span style='color: #808080;'>Prezado Servidor(a) " + "<strong>"
										+ idNova.getDpPessoa().getNomePessoa() + "</strong>" + " do(a) " + "<strong>"
										+ idNova.getDpPessoa().getOrgaoUsuario().getDescricao() + "</strong>"
										+ ",</span></h4>"
										+ "<p><span style='color: #808080;'>Voc&ecirc; est&aacute; recebendo sua nova senha para acesso "
										+ "ao Portal SP Sem Papel.</span></p>"
										+ "<p><span style='color: #808080;'><strong>"
										+ "<p><span style='color: #808080;'>Sua matr&iacute;cula &eacute;:&nbsp;&nbsp;<strong>"
										+ idNova.getDpPessoa().getSigla() + "</strong></span></p>"
										+ "<p><span style='color: #808080;'>Sua senha &eacute;:&nbsp;&nbsp;<strong>"
										+ novaSenha + "</strong></span></p>" + "</div>" + "</td>" + "</tr>" + "<tr>"
										+ "<td style='height: 18px; padding: 0 20px; background-color: #eaecee;'>"
										+ "<p><span style='color: #aaa;'><strong>Aten&ccedil;&atilde;o:</strong> esta &eacute; uma mensagem autom&aacute;tica. Por favor n&atilde;o responda&nbsp;</span></p>"
										+ "</td>" + "</tr>" + "</tbody>" + "</table>");
					} else {
						Correio.enviar(pessoa.getEmailPessoaAtual(), "Alteração de senha ",
								"\n" + idNova.getDpPessoa().getNomePessoa() + "\nMatricula: "
										+ idNova.getDpPessoa().getSigla() + "\n" + "\nSua senha foi alterada para: "
										+ novaSenha + "\n\n Atenção: esta é uma "
										+ "mensagem automática. Por favor, não responda. ");
					}

					return idNova;
				} catch (final Exception e) {
					dao().rollbackTransacao();
					throw new AplicacaoException("Ocorreu um erro durante a gravação", 0, e);
				}
			} else {
				throw new AplicacaoException("Este usuário não está cadastrado no sistema");
			}

		} else {
			if (pessoa == null) {
				throw new AplicacaoException(
						SigaMessages.getMessage("usuario.erro.cpfmatriculanaocadastrado"));
			} else if (pessoa.getEmailPessoaAtual() == null) {
				throw new AplicacaoException("Este usuário não possui e-mail cadastrado");
			} else {
				throw new AplicacaoException("Dados Incorretos!");
			}
		}
	}

	public CpIdentidade criarIdentidade(String matricula, String cpf, CpIdentidade idCadastrante,
			final String senhaDefinida, String[] senhaGerada, boolean marcarParaSinc) throws AplicacaoException {

		Long longCpf = CPFUtils.getLongValueValidaSimples(cpf);
		final List<DpPessoa> listaPessoas = dao().listarPorCpf(longCpf);
		if (listaPessoas.isEmpty()) {
			throw new AplicacaoException("O CPF informado está incorreto, tente novamente!");
		}

		final long longMatricula = MatriculaUtils.getParteNumericaDaMatricula(matricula);

		final DpPessoa pessoa = dao().consultarPorCpfMatricula(longCpf, longMatricula);

		String siglaOrgaoMatricula = MatriculaUtils.getSiglaDoOrgaoDaMatricula(matricula);
		boolean autenticaPeloBanco = buscarModoAutenticacao(siglaOrgaoMatricula)
				.equals(GiService._MODO_AUTENTICACAO_BANCO);

		if (pessoa != null && matricula.equals(pessoa.getSigla())) {
			CpIdentidade id;
			try {
				id = dao().consultaIdentidadeCadastrante(matricula, true);
			} catch (Exception e1) {
				id = null;
			}
			if (id == null) {
				if (pessoa.getEmailPessoaAtual() != null) {
					String novaSenha = "";
					if (autenticaPeloBanco) {
						if (senhaDefinida != null && senhaDefinida.length() > 0) {
							novaSenha = senhaDefinida;
						} else {
							novaSenha = GeraMessageDigest.geraSenha();
						}

						if (senhaGerada[0] != null) {
							novaSenha = senhaGerada[0];
						}
					}
					try {
						CpIdentidade idNova = new CpIdentidade();
						String hashNova = "";
						if (autenticaPeloBanco) {
							hashNova = GeraMessageDigest.executaHash(novaSenha.getBytes(), "MD5");
						}
						idNova.setDscSenhaIdentidade(hashNova);
						idNova.setNmLoginIdentidade(matricula);
						idNova.setDpPessoa(pessoa);
						idNova.setDtCriacaoIdentidade(dao().consultarDataEHoraDoServidor());
						idNova.setCpOrgaoUsuario(pessoa.getOrgaoUsuario());
						idNova.setCpTipoIdentidade(dao().consultar(1, CpTipoIdentidade.class, false));
						idNova.setHisDtIni(idNova.getDtCriacaoIdentidade());

						dao().iniciarTransacao();
						dao().gravarComHistorico(idNova, idCadastrante);
						dao().commitTransacao();

						if (SigaMessages.isSigaSP()) {
							String[] destinanarios = { pessoa.getEmailPessoaAtual() };
							String conteudoHTML = pessoaIsUsuarioExterno(pessoa)
									? textoEmailNovoUsuarioExternoSP(idNova, matricula, novaSenha)
									: textoEmailNovoUsuarioSP(idNova, matricula, novaSenha, autenticaPeloBanco);
							
							Correio.enviar(null,
									destinanarios, "Novo Usuário", "", conteudoHTML);
						} else {
							Correio.enviar(pessoa.getEmailPessoaAtual(), "Novo Usuário",
									textoEmailNovoUsuario(matricula, novaSenha, autenticaPeloBanco));
						}
						dao().commitTransacao();
						return idNova;
					} catch (final Exception e) {
						dao().rollbackTransacao();
						throw new AplicacaoException(
								"Ocorreu um erro durante a gravação no banco de dados ou no envio do email", 0, e);
					}
				} else {
					throw new AplicacaoException("Este usuário não possui e-mail cadastrado");
				}

			} else {
				throw new AplicacaoException("Usuário já está cadastrado no sistema");
			}

		} else {
			if (pessoa == null) {
				throw new AplicacaoException(
						SigaMessages.getMessage("usuario.erro.cpfmatriculanaocadastrado"));
			} else {
				throw new AplicacaoException("Dados Incorretos!");
			}
		}

	}

	/**
	 * Verifica se a {@link DpPessoa Pessoa} é um usuário externo. <br>
	 * <b>NOTA DE IMPLEMENTAÇÃO:</b> Originalmente dentro de
	 * {@link #criarIdentidade(String, String, CpIdentidade, String, String[], boolean)}
	 * era chamado {@link DpPessoa#isUsuarioExterno()}, que faz uso de
	 * {@link DpPessoa#getLotacao() lotação}. Entretanto durante a migração para o
	 * EAP 7.2, verificou-se que quando o método <code>criarIdentidade</code> era
	 * chamado a partir de
	 * {@link #criarUsuario(String, Long, Long, Long, Long, Long, String, String, String, String)},
	 * ao se chamar {@link DpPessoa#getLotacao()} dentro de
	 * {@link DpPessoa#isUsuarioExterno()} acontecia um
	 * {@link NullPointerException}, mesmo que essa pessoa tivesse acabado de ter
	 * sido carregada pelo Hibernate. Entretanto, se for chamado
	 * <code>pessoa.getLotacao().getId()</code> não houve problema. <br/>
	 * A solução foi emular a implmentação de {@link DpPessoa#isUsuarioExterno()},
	 * usar essa <code>pessoa.getLotacao().getId()</code>, para consultar a Lotação
	 * na base de dados através de
	 * {@link ModeloDao#consultar(java.io.Serializable, Class, boolean)} e chamar
	 * {@link DpLotacao#getIsExternaLotacao()}.
	 * 
	 * @param pessoa Pessoa a ser avaliada.
	 * @return se a Pessoa é um usuário externo.
	 */
	private boolean pessoaIsUsuarioExterno(DpPessoa pessoa) {
		if (pessoa.getOrgaoUsuario() == null && pessoa.getLotacao() == null) {
			return false;
		}		
		
		CpOrgaoUsuario orgaoUsuario = pessoa.getOrgaoUsuario();
		Integer isExternoOrgaoUsu = orgaoUsuario.getIsExternoOrgaoUsu();
		if ((isExternoOrgaoUsu != null) && (isExternoOrgaoUsu == 1)) {
			return true;
		}

		Long idLotacao = pessoa.getLotacao().getId();
		if (idLotacao == null) {
			return false;
		}

		DpLotacao lotacao = dao().consultar(idLotacao, DpLotacao.class, false);
		return (lotacao != null) && (lotacao.getIsExternaLotacao() == 1);
	}

	private String textoEmailNovoUsuario(String matricula, String novaSenha, boolean autenticaPeloBanco) {
		StringBuffer retorno = new StringBuffer();

		retorno.append("Seu login é: ");
		retorno.append(matricula);
		retorno.append("\n e sua senha é ");
		if (autenticaPeloBanco) {
			retorno.append(novaSenha);
		} else {
			retorno.append("a mesma usada para logon na rede (Windows).");
		}
		retorno.append("\n\n Atenção: esta é uma ");
		retorno.append("mensagem automática. Por favor não responda ");

		return retorno.toString();
	}

	private String textoEmailNovoUsuarioSP(CpIdentidade identidade, String matricula, String novaSenha,
			boolean autenticaPeloBanco) {
		StringBuffer retorno = new StringBuffer();

		retorno.append("<table>");
		retorno.append("<tbody>");
		retorno.append("<tr>");
		retorno.append("<td style='height: 80px; background-color: #f6f5f6; padding: 10px 20px;'>");
		retorno.append(
				"<img style='padding: 10px 0px; text-align: center;' src='https://www.documentos.spsempapel.sp.gov.br/siga/imagens/logo-sem-papel-cor.png' ");
		retorno.append("alt='SP Sem Papel' width='108' height='50' /></td>");
		retorno.append("</tr>");
		retorno.append("<tr>");
		retorno.append("<td style='background-color: #bbb; padding: 0 20px;'>");
		retorno.append("<h3 style='height: 20px;'>Governo do Estado de S&atilde;o Paulo</h3>");
		retorno.append("</td>");
		retorno.append("</tr>");
		retorno.append("<tr style='height: 310px;'>");
		retorno.append("<td style='height: 310px; padding: 10px 20px;'>");
		retorno.append("<div>");
		retorno.append("<p><span style='color: #808080;'>Prezado Servidor(a) ");
		retorno.append("<strong>" + identidade.getDpPessoa().getNomePessoa() + "</strong>");
		retorno.append(" do(a) ");
		retorno.append("<strong>" + identidade.getDpPessoa().getOrgaoUsuario().getDescricao() + "</strong>");
		retorno.append(",</span></h4>");
		retorno.append(
				"<p><span style='color: #808080;'>Voc&ecirc; est&aacute; recebendo sua matr&iacute;cula e senha para acesso ");
		retorno.append("ao Portal SP Sem Papel, para acesso ao servi&ccedil;o Documentos Digitais.</span></p>");
		retorno.append(
				"<p><span style='color: #808080;'>Ao usar o portal para cria&ccedil;&atilde;o de documentos, voc&ecirc; est&aacute; ");
		retorno.append("produzindo documento nato-digital, confirme seus dados cadastrais, nome, cargo e unidade ");
		retorno.append("antes de iniciar o uso e assinar documentos.</span></p>");
		retorno.append(
				"<p><span style='color: #808080;'>Realize sua capacita&ccedil;&atilde;o no AVA e utilize o ambiente ");
		retorno.append("de capacita&ccedil;&atilde;o para testes e treinamento.</span></p>");
		retorno.append("<p><span style='color: #808080;'>Sua matr&iacute;cula &eacute;:&nbsp;&nbsp;<strong>");
		retorno.append(matricula);
		retorno.append("</strong></span></p>");
		if (autenticaPeloBanco) {
			retorno.append("<p><span style='color: #808080;'>Sua senha &eacute;:&nbsp;&nbsp;<strong>");
			retorno.append(novaSenha);
			retorno.append("</strong></span></p>");
		} else {
			retorno.append("<p><span style='color: #808080;'>");
			retorno.append("Sua senha &eacute; a mesma usada para logon na rede (Windows).");
			retorno.append("</span></p>");
		}
		retorno.append("</div>");
		retorno.append("</td>");
		retorno.append("</tr>");
		retorno.append("<tr>");
		retorno.append("<td style='height: 18px; padding: 0 20px; background-color: #eaecee;'>");
		retorno.append(
				"<p><span style='color: #aaa;'><strong>Aten&ccedil;&atilde;o:</strong> esta &eacute; uma mensagem autom&aacute;tica. Por favor n&atilde;o responda&nbsp;</span></p>");
		retorno.append("</td>");
		retorno.append("</tr>");
		retorno.append("</tbody>");
		retorno.append("</table>");
		return retorno.toString();
	}
	
	private String textoEmailNovoUsuarioExternoSP(CpIdentidade identidade, String matricula, String novaSenha) {		
		String conteudo = "";
		
		try (BufferedReader bfr = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/templates/email/novo-usuario-externo.html")))) {			
			String str;
			
			while((str = bfr.readLine()) != null) {
				conteudo += str;
			}
			
		} catch (IOException e) {
			throw new AplicacaoException("Erro ao montar e-mail para enviar ao usuário externo " + identidade.getDpPessoa().getNomePessoa());
		}
		
		
		conteudo = conteudo.replace("${nomeUsuario}", identidade.getDpPessoa().getNomePessoa())
			.replace("${cpfUsuario}", matricula)
			.replace("${url}", Prop.get("/sigaex.url").replace("/sigaex/app", ""))
			.replace("${senhaUsuario}", novaSenha);
		
		return conteudo;
	}

	public String buscarModoAutenticacao(String orgao) {
		String retorno = GiService._MODO_AUTENTICACAO_DEFAULT;
		if (Prop.get("/siga.ldap.orgaos") == null)
			return retorno;
		String modo = Prop.get("/siga.ldap." + orgao.toLowerCase() + ".modo");
		if (modo != null)
			retorno = modo;
		return retorno;
	}

	public CpIdentidade trocarSenhaDeIdentidade(String senhaAtual, String senhaNova, String senhaConfirma,
			String nomeUsuario, CpIdentidade idCadastrante) throws NoSuchAlgorithmException, AplicacaoException {

		// usuario existe?
		final CpIdentidade id = dao().consultaIdentidadeCadastrante(nomeUsuario, true);
		if (id == null)
			throw new AplicacaoException("O usuário não está cadastrado.");

		boolean autenticaPeloBanco = buscarModoAutenticacao(id.getCpOrgaoUsuario().getSiglaOrgaoUsu())
				.equals(GiService._MODO_AUTENTICACAO_BANCO);
		if (!autenticaPeloBanco)
			throw new AplicacaoException("O usuário deve modificar sua senha usando a interface do Windows "
					+ "(acionando as teclas Ctrl, Alt e Del / Delete, opção 'Alterar uma senha')"
					+ ", ou entrando em contato com a Central de Atendimento.");

		// preencheu senha atual?
		if (senhaAtual == null || senhaAtual.trim().length() == 0) {
			throw new AplicacaoException("Senha atual não confere");
		}

		boolean podeTrocar = false;
		boolean podeTrocarSenhaAdm = false;

		podeTrocar = autenticarViaBanco(senhaAtual, id);

		if (!podeTrocar) {
			// tenta o modo administrador... Podendo autenticar o ADM pelo LDAP
			String servico = "SIGA: Sistema Integrado de Gestão Administrativa;GI: Módulo de Gestão de Identidade;DEF_SENHA: Definir Senha";
			try {
				boolean admTrocaSenha = Cp.getInstance().getConf().podeUtilizarServicoPorConfiguracao(
						idCadastrante.getDpPessoa(), idCadastrante.getDpPessoa().getPessoaAtual().getLotacao(), servico);
				if (admTrocaSenha) {
					if (buscarModoAutenticacao(idCadastrante.getCpOrgaoUsuario().getSiglaOrgaoUsu())
							.equals(GiService._MODO_AUTENTICACAO_BANCO)) {
						podeTrocarSenhaAdm = autenticarViaBanco(senhaAtual, idCadastrante);
					} else {
						podeTrocarSenhaAdm = autenticarViaLdap(senhaAtual, idCadastrante);
					}

					if (!podeTrocarSenhaAdm)
						throw new AplicacaoException("Senha atual não confere");

					try {
						Correio.enviar(id.getDpPessoa().getEmailPessoaAtual(), "Troca de Senha",
								"O Administrador do sistema alterou a senha do seguinte usuário, para efetuar "
										+ "uma manutenção no sistema: " + "\n" + "\n - Nome: "
										+ id.getDpPessoa().getNomePessoa() + "\n - Matricula: "
										+ id.getDpPessoa().getSigla() + "\n - Senha: " + senhaNova
										+ "\n\n Antes de utiliza-lo novamente, altere a sua senha "
										+ "ou solicite uma nova através da opção 'esqueci minha senha'"
										+ "\n\n Atenção: esta é uma "
										+ "mensagem automática. Por favor, não responda.");
					} catch (Exception e) {
						System.out.println(
								"Erro: Não foi possível enviar e-mail para o usuário informando que o administrador do sistema alterou sua senha."
										+ "\n" + "\n - Nome: " + id.getDpPessoa().getNomePessoa() + "\n - Matricula: "
										+ id.getDpPessoa().getSigla());
					}
				}
			} catch (Exception e1) {

			}
		}

		if ((podeTrocar || podeTrocarSenhaAdm) && senhaNova.equals(senhaConfirma)) {
			try {
				Date dt = dao().consultarDataEHoraDoServidor();
				CpIdentidade idNova = new CpIdentidade();
				PropertyUtils.copyProperties(idNova, id);
				idNova.setIdIdentidade(null);
				idNova.setDtCriacaoIdentidade(dt);
				final String hashNova = GeraMessageDigest.executaHash(senhaNova.getBytes(), "MD5");
				idNova.setDscSenhaIdentidade(hashNova);

				// BASE64Encoder encoderBase64 = new BASE64Encoder();
				// String chave = encoderBase64.encode(id.getDpPessoa()
				// .getIdInicial().toString().getBytes());
				// String senhaCripto = encoderBase64.encode(Criptografia
				// .criptografar(senhaNova, chave));
				idNova.setDscSenhaIdentidadeCripto(null);
				idNova.setDscSenhaIdentidadeCriptoSinc(null);

				dao().iniciarTransacao();
				dao().gravarComHistorico(idNova, id, dt, idCadastrante);
				dao().commitTransacao();
				return idNova;
			} catch (final Exception e) {
				dao().rollbackTransacao();
				throw new AplicacaoException("Ocorreu um erro durante a gravação", 0, e);
			}
		} else {
			throw new AplicacaoException("Senha Atual não confere e/ou Senha nova diferente de confirmação");
		}
	}

	public CpIdentidade trocarSenhaDeIdentidadeGovSp(String senhaAtual, String senhaNova, String senhaConfirma,
			String nomeUsuario, CpIdentidade idCadastrante, List<CpIdentidade> listaIdentidades)
			throws NoSuchAlgorithmException, AplicacaoException {
		if (senhaAtual == null || senhaAtual.trim().length() == 0) {
			throw new AplicacaoException("Senha atual não confere");
		}
		final String hashAtual = GeraMessageDigest.executaHash(senhaAtual.getBytes(), "MD5");

		final CpIdentidade id = dao().consultaIdentidadeCadastrante(nomeUsuario, true);
		// se o usuário não existir
		if (id == null)
			throw new AplicacaoException("O usuário não está cadastrado.");

		boolean podeTrocar = Boolean.FALSE;

		for (CpIdentidade cpIdentidade : listaIdentidades) {
			if (cpIdentidade.getDscSenhaIdentidade().equals(hashAtual)) {
				podeTrocar = Boolean.TRUE;
				break;
			}
		}

		if (!podeTrocar) {
			// tenta o modo administrador...
			String servico = "SIGA: Sistema Integrado de Gestão Administrativa;GI: Módulo de Gestão de Identidade;DEF_SENHA: Definir Senha";
			try {
				if (Cp.getInstance().getConf().podeUtilizarServicoPorConfiguracao(idCadastrante.getDpPessoa(),
						idCadastrante.getDpPessoa().getLotacao(), servico)) {

					if (hashAtual.equals(idCadastrante.getDscSenhaIdentidade())) {
						podeTrocar = true;
					} else {
						throw new AplicacaoException("Senha atual não confere");
					}

					try {
						Correio.enviar(id.getDpPessoa().getEmailPessoaAtual(), "Troca de Senha",
								"O Administrador do sistema alterou a senha do seguinte usuário, para efetuar "
										+ "uma manutenção no sistema: " + "\n" + "\n - Nome: "
										+ id.getDpPessoa().getNomePessoa() + "\n - Matricula: "
										+ id.getDpPessoa().getSigla() + "\n - Senha: " + senhaNova
										+ "\n\n Antes de utiliza-lo novamente, altere a sua senha "
										+ "ou solicite uma nova através da opção 'esqueci minha senha'"
										+ "\n\n Atenção: esta é uma "
										+ "mensagem automática. Por favor, não responda.");
					} catch (Exception e) {
						System.out.println(
								"Erro: Não foi possível enviar e-mail para o usuário informando que o administrador do sistema alterou sua senha."
										+ "\n" + "\n - Nome: " + id.getDpPessoa().getNomePessoa() + "\n - Matricula: "
										+ id.getDpPessoa().getSigla());
					}
				}
			} catch (Exception e1) {

			}
		}

		if (podeTrocar && senhaNova.equals(senhaConfirma)) {
			try {
				Date dt = dao().consultarDataEHoraDoServidor();
				final String hashNova = GeraMessageDigest.executaHash(senhaNova.getBytes(), "MD5");

				dao().iniciarTransacao();
				CpIdentidade i = null;
				for (CpIdentidade cpIdentidade : listaIdentidades) {
					i = new CpIdentidade();
					PropertyUtils.copyProperties(i, cpIdentidade);
					i.setIdIdentidade(null);
					i.setDtCriacaoIdentidade(dt);
					i.setDscSenhaIdentidade(hashNova);
					i.setDscSenhaIdentidadeCripto(null);
					i.setDscSenhaIdentidadeCriptoSinc(null);
					dao().gravarComHistorico(i, cpIdentidade, dt, idCadastrante);
				}

				dao().commitTransacao();
				return null;
			} catch (final Exception e) {
				dao().rollbackTransacao();
				throw new AplicacaoException("Ocorreu um erro durante a gravação", 0, e);
			}
		} else {
			throw new AplicacaoException("Senha Atual não confere e/ou Senha nova diferente de confirmação");
		}
	}

	private boolean autenticarViaBanco(String senhaAtual, final CpIdentidade id) throws NoSuchAlgorithmException {
		String hashAtual = GeraMessageDigest.executaHash(senhaAtual.getBytes(), "MD5");
		return id.getDscSenhaIdentidade().equals(hashAtual);
	}

	private boolean autenticarViaLdap(String login, String senhaAtual) {
		boolean podeTrocar;
		try {
			podeTrocar = IntegracaoLdapViaWebService.getInstancia().autenticarUsuario(login, senhaAtual);
		} catch (Exception e) {
			podeTrocar = false;
		}
		return podeTrocar;
	}

	private boolean autenticarViaLdap(String senhaAtual, final CpIdentidade id) {
		return autenticarViaLdap(id.getNmLoginIdentidade(), senhaAtual);
	}

	public boolean podeAlterarSenha(String auxiliar1, String cpf1, String senha1, String auxiliar2, String cpf2,
			String senha2, String matricula, String cpf, String novaSenha) throws AplicacaoException {
		try {
			final long matAux1 = Long.parseLong(auxiliar1.substring(2));
			final DpPessoa pesAux1 = dao().consultarPorCpfMatricula(Long.parseLong(cpf1), matAux1);
			if (pesAux1 == null) {
				throw new AplicacaoException("Auxiliar 1 inválido!");
			}

			final long matAux2 = Long.parseLong(auxiliar2.substring(2));
			final DpPessoa pesAux2 = dao().consultarPorCpfMatricula(Long.parseLong(cpf2), matAux2);
			if (pesAux2 == null) {
				throw new AplicacaoException("Auxiliar 2 inválido!");
			}

			final long longmatricula = Long.parseLong(matricula.substring(2));
			final DpPessoa pessoa = dao().consultarPorCpfMatricula(Long.parseLong(cpf), longmatricula);
			if (pessoa == null) {
				throw new AplicacaoException("A pessoa que terá a senha definida inválida!");
			}

			CpIdentidade cpIdAux1 = null;
			CpIdentidade cpIdAux2 = null;
			for (CpIdentidade cpId : dao().consultaIdentidades(pesAux1)) {
				if (cpId.getCpTipoIdentidade().getIdCpTpIdentidade().equals(1)) {
					cpIdAux1 = cpId;
				}
			}

			for (CpIdentidade cpId : dao().consultaIdentidades(pesAux2)) {
				if (cpId.getCpTipoIdentidade().getIdCpTpIdentidade().equals(1)) {
					cpIdAux2 = cpId;
				}
			}

			if (cpIdAux1 == null || cpIdAux2 == null) {
				throw new AplicacaoException("Problema ao localizar a identidade dos auxiliares!");
			}

			if (pesAux1.getIdInicial().equals(pesAux2.getIdInicial())
					|| pesAux1.getIdInicial().equals(pessoa.getIdInicial())
					|| pesAux2.getIdInicial().equals(pessoa.getIdInicial())) {
				throw new AplicacaoException("As pessoas devem ser diferentes!");
			}
			;
			if (!autenticarViaBanco(senha1, cpIdAux1) || !autenticarViaBanco(senha2, cpIdAux2)) {
				throw new AplicacaoException("As senhas não conferem!");
			}

			List<DpPessoa> auxiliares = new ArrayList<DpPessoa>();
			auxiliares.add(pesAux1);
			auxiliares.add(pesAux2);

			if (isAuxAdministradores(pesAux1, pesAux2)) {
				return true;
			}

			if (!pessoasMesmaLotacaoOuSuperior(pessoa, auxiliares)) {
				throw new AplicacaoException(
						"Os auxiliares devem ser da mesma lotação do usuário que terá a senha trocada!\n Também é permitido que pessoas da lotação imediatamente superior na hiearquia sejam auxiliares.");
			}

		} catch (Exception e) {
			throw new AplicacaoException(e.getMessage());
		}
		return true;

	}

	private boolean isAuxAdministradores(DpPessoa aux1, DpPessoa aux2) {

		String servico = "SIGA: Sistema Integrado de Gestão Administrativa;GI: Módulo de Gestão de Identidade;DEF_SENHA: Definir Senha";
		try {

			return Cp.getInstance().getConf().podeUtilizarServicoPorConfiguracao(aux1, aux1.getLotacao(), servico)
					&& Cp.getInstance().getConf().podeUtilizarServicoPorConfiguracao(aux2, aux2.getLotacao(), servico);

		} catch (Exception e) {
			return false;
		}

	}

	/**
	 * Verifica se as pessoas são da mesma lotação ou da lotação imadiatamente
	 * superior da pessoa-alvo
	 * 
	 * @return
	 */
	private boolean pessoasMesmaLotacaoOuSuperior(DpPessoa alvo, List<DpPessoa> listaPessoas) {

		for (DpPessoa p : listaPessoas) {
			if ((alvo.getLotacao().getIdInicial().equals(p.getLotacao().getIdInicial())
					|| (alvo.getLotacao().getLotacaoPai().getIdInicial().equals(p.getLotacao().getIdInicial())))) {
				continue;
			}
			return false;
		}
		return true;
	}

	public CpIdentidade definirSenhaDeIdentidade(String senhaNova, String senhaConfirma, String nomeUsuario,
			String auxiliar1, String auxiliar2, CpIdentidade idCadastrante)
			throws NoSuchAlgorithmException, AplicacaoException {

		final CpIdentidade id = dao().consultaIdentidadeCadastrante(nomeUsuario, true);
		// se o usuário não existir
		if (id == null)
			throw new AplicacaoException("O usuário não está cadastrado.");

		if (senhaNova.equals(senhaConfirma)) {
			try {
				Date dt = dao().consultarDataEHoraDoServidor();
				CpIdentidade idNova = new CpIdentidade();
				PropertyUtils.copyProperties(idNova, id);
				idNova.setIdIdentidade(null);
				idNova.setDtCriacaoIdentidade(dt);
				final String hashNova = GeraMessageDigest.executaHash(senhaNova.getBytes(), "MD5");
				idNova.setDscSenhaIdentidade(hashNova);

				idNova.setDscSenhaIdentidadeCripto(null);
				idNova.setDscSenhaIdentidadeCriptoSinc(null);

				dao().iniciarTransacao();
				dao().gravarComHistorico(idNova, id, dt, idCadastrante);
				dao().commitTransacao();
				Correio.enviar(id.getDpPessoa().getEmailPessoaAtual(), "Alteração de senha ",
						"\n" + idNova.getDpPessoa().getNomePessoa() + "\nMatricula: " + idNova.getDpPessoa().getSigla()
								+ "\n" + "\nSua senha foi alterada para: " + senhaNova
								+ ".\n\n As seguintes pessoas participaram da alteração da senha: " + auxiliar1 + " e "
								+ auxiliar2 + ".\n\n Atenção: esta é uma "
								+ "mensagem automática. Por favor, não responda. ");
				return idNova;
			} catch (final Exception e) {
				dao().rollbackTransacao();
				throw new AplicacaoException("Ocorreu um erro durante a gravação", 0, e);
			}
		} else {
			throw new AplicacaoException("Senha nova diferente de confirmação");
		}
	}

	public CpModelo alterarCpModelo(CpModelo mod, String conteudo, CpIdentidade identidadeCadastrante)
			throws AplicacaoException {
		try {
			Date dt = dao().consultarDataEHoraDoServidor();
			CpModelo modNew = new CpModelo();
			try {
				PropertyUtils.copyProperties(modNew, mod);
			} catch (Exception e) {
				throw new AplicacaoException("Erro ao copiar as propriedades do modelo anterior.");
			}
			modNew.setIdMod(null);
			modNew.setConteudoBlobString(conteudo);

			dao().iniciarTransacao();
			CpModelo modSaved = (CpModelo) dao().gravarComHistorico(modNew, mod, dt, identidadeCadastrante);
			dao().commitTransacao();
			return modSaved;
		} catch (Exception e) {
			dao().rollbackTransacao();
			throw new AplicacaoException("Não foi possível alterar o modelo.", 9, e);
		}
	}

	public void appException(String message) throws AplicacaoException {
		appException(message, null);
	}

	private void appException(String message, Throwable cause) throws AplicacaoException {
		throw new AplicacaoException(message, 0, cause);
	}

	public void logAcesso(CpTipoAcessoEnum tipo, String principal, int iat, int exp, String auditIP) {
		CpIdentidade identidade = dao().consultaIdentidadeCadastrante(principal, true);
		CpAcesso acesso = new CpAcesso();
		acesso.setCpIdentidade(identidade);
		acesso.setDtInicio(new Date(iat * 1000L));
		acesso.setDtTermino(new Date(exp * 1000L));
		acesso.setTipo(tipo);
		acesso.setAuditIP(auditIP);
		dao().gravar(acesso);
	}

	public InputStream uploadLotacao(File file, CpOrgaoUsuario orgaoUsuario, String extensao) {			
		Excel excel = new Excel();		
		return excel.uploadLotacao(file, orgaoUsuario, extensao);
	}

	public InputStream uploadFuncao(File file, CpOrgaoUsuario orgaoUsuario, String extensao) {
		InputStream inputStream = null;
		try {
			Excel excel = new Excel();
			inputStream = excel.uploadFuncao(file, orgaoUsuario, extensao);
		} catch (Exception e) {

		}
		return inputStream;
	}

	public InputStream uploadCargo(File file, CpOrgaoUsuario orgaoUsuario, String extensao) {
		InputStream inputStream = null;
		try {
			Excel excel = new Excel();
			inputStream = excel.uploadCargo(file, orgaoUsuario, extensao);
		} catch (Exception e) {

		}
		return inputStream;
	}

	public InputStream uploadPessoa(File file, CpOrgaoUsuario orgaoUsuario, String extensao, CpIdentidade i) {
		Excel excel = new Excel();				
		return excel.uploadPessoa(file, orgaoUsuario, extensao, i);
	}

	public String criarUsuario(String cadastranteStr, final Long id, final Long idOrgaoUsu, final Long idCargo,
			final Long idFuncao, final Long idLotacao, final String nmPessoa, final String dtNascimento,
			final String cpf, final String email) throws Exception {

		if (idOrgaoUsu == null || idOrgaoUsu == 0)
			throw new AplicacaoException("Órgão não Localizado");

		if (idCargo == null || idCargo == 0)
			throw new AplicacaoException("Cargo não Localizado");

		if (idLotacao == null || idLotacao == 0)
			throw new AplicacaoException("Lotação não Localizado");

		if (nmPessoa == null || nmPessoa.trim() == "")
			throw new AplicacaoException("Nome não informado");

		if (cpf == null || cpf.trim() == "")
			throw new AplicacaoException("CPF não informado");

		if (email == null || email.trim() == "")
			throw new AplicacaoException("E-mail não informado");

		if (nmPessoa != null && !nmPessoa.matches("[a-zA-ZáâãéêíóôõúçÁÂÃÉÊÍÓÔÕÚÇ'' ]+"))
			throw new AplicacaoException("Nome com caracteres não permitidos");

		DpPessoa pessoa = new DpPessoa();

		if (id == null) {
			Date data = new Date(System.currentTimeMillis());
			pessoa.setDataInicio(data);
			pessoa.setMatricula(0L);
			pessoa.setSituacaoFuncionalPessoa(SituacaoFuncionalEnum.APENAS_ATIVOS.getValor()[0]);
		} else {
			pessoa = dao().consultar(id, DpPessoa.class, false);
		}

		if (dtNascimento != null && !"".equals(dtNascimento)) {
			Date dtNasc = new Date();
			dtNasc = SigaCalendar.converteStringEmData(dtNascimento);

			Calendar hj = Calendar.getInstance();
			Calendar dtNasci = new GregorianCalendar();
			dtNasci.setTime(dtNasc);

			if (hj.before(dtNasci)) {
				throw new AplicacaoException("Data de nascimento inválida");
			}
			pessoa.setDataNascimento(dtNasc);
		} else {
			pessoa.setDataNascimento(null);
		}

		pessoa.setNomePessoa(Texto.removerEspacosExtra(nmPessoa).trim());
		pessoa.setCpfPessoa(Long.valueOf(cpf.replace("-", "").replace(".", "")));
		pessoa.setEmailPessoa(Texto.removerEspacosExtra(email).trim().replace(" ", "").toLowerCase());

		CpOrgaoUsuario ou = new CpOrgaoUsuario();
		DpCargo cargo = new DpCargo();
		DpFuncaoConfianca funcao = new DpFuncaoConfianca();
		DpLotacao lotacao = new DpLotacao();

		ou.setIdOrgaoUsu(idOrgaoUsu);
		ou = CpDao.getInstance().consultarPorId(ou);
		cargo.setId(idCargo);
		lotacao.setId(idLotacao);
		funcao.setIdFuncao(idFuncao);

		pessoa.setOrgaoUsuario(ou);
		pessoa.setCargo(cargo);
		pessoa.setLotacao(lotacao);

		if (idFuncao != null && idFuncao != 0) {
			pessoa.setFuncaoConfianca(funcao);
		} else {
			pessoa.setFuncaoConfianca(null);
		}
		pessoa.setSesbPessoa(ou.getSigla());

		// ÓRGÃO / CARGO / FUNÇÃO DE CONFIANÇA / LOTAÇÃO e CPF iguais.
		DpPessoaDaoFiltro dpPessoa = new DpPessoaDaoFiltro();
		dpPessoa.setIdOrgaoUsu(pessoa.getOrgaoUsuario().getId());
		dpPessoa.setCargo(pessoa.getCargo());
		dpPessoa.setFuncaoConfianca(pessoa.getFuncaoConfianca());
		dpPessoa.setLotacao(pessoa.getLotacao());
		dpPessoa.setCpf(pessoa.getCpfPessoa());
		dpPessoa.setNome("");
		dpPessoa.setId(id);

		dpPessoa.setBuscarFechadas(Boolean.FALSE);
		Integer tamanho = dao().consultarQuantidade(dpPessoa);

		if (tamanho > 0) {
			throw new AplicacaoException("Usuário já cadastrado com estes dados: Órgão, Cargo, Função, Unidade e CPF");
		}

		try {
			dao().iniciarTransacao();
			dao().gravar(pessoa);
			if (pessoa.getIdPessoaIni() == null && pessoa.getId() != null) {
				pessoa.setIdPessoaIni(pessoa.getId());
				pessoa.setIdePessoa(pessoa.getId().toString());
				pessoa.setMatricula(10000 + pessoa.getId());
				pessoa.setIdePessoa(pessoa.getMatricula().toString());
				dao().gravar(pessoa);

				List<CpIdentidade> lista = CpDao.getInstance()
						.consultaIdentidadesPorCpf(cpf.replace(".", "").replace("-", ""));
				CpIdentidade usu = null;
				if (lista.size() > 0) {
					CpIdentidade usuarioExiste = lista.get(0);
					usu = new CpIdentidade();
					usu.setCpTipoIdentidade(dao().consultar(1, CpTipoIdentidade.class, false));
					usu.setDscSenhaIdentidade(usuarioExiste.getDscSenhaIdentidade());
					usu.setDtCriacaoIdentidade(dao().consultarDataEHoraDoServidor());
					usu.setCpOrgaoUsuario(ou);
					usu.setHisDtIni(usu.getDtCriacaoIdentidade());
					usu.setHisAtivo(1);
				}

				if (usu != null) {
					usu.setNmLoginIdentidade(pessoa.getSesbPessoa() + pessoa.getMatricula());
					usu.setDpPessoa(pessoa);
					// dao().gravarComHistorico(usu,
					// getIdentidadeCadastrante());
				}
			}
			dao().commitTransacao();
		} catch (final Exception e) {
			dao().rollbackTransacao();
			throw new AplicacaoException("Erro na gravação", 0, e);
		}

		String[] senhaGerada = new String[1];

		CpIdentidade idIdentidade;
		try {
			idIdentidade = dao().consultaIdentidadeCadastrante(cadastranteStr, true);
		} catch (Exception e1) {
			idIdentidade = null;
		}

		senhaGerada[0] = GeraMessageDigest.geraSenha();
		Cp.getInstance().getBL().criarIdentidade(pessoa.getSesbPessoa() + pessoa.getMatricula(),
				pessoa.getCpfFormatado(), idIdentidade, null, senhaGerada, Boolean.FALSE);

		return "Usuário cadastrado com sucesso: " + pessoa.getSesbPessoa() + pessoa.getMatricula();

	}

	public String inativarUsuario(final Long idUsuario) {
		CpOrgaoUsuario ou = new CpOrgaoUsuario();
		DpPessoa pessoa = dao().consultar(idUsuario, DpPessoa.class, false);
		ou.setIdOrgaoUsu(pessoa.getOrgaoUsuario().getId());
		ou = CpDao.getInstance().consultarPorId(ou);

		pessoa = dao().consultar(idUsuario, DpPessoa.class, false);
		// inativar
		if (pessoa.getDataFimPessoa() == null || "".equals(pessoa.getDataFimPessoa())) {
			Calendar calendar = new GregorianCalendar();
			Date date = new Date();
			calendar.setTime(date);
			pessoa.setDataFimPessoa(calendar.getTime());

		}

		try {
			dao().iniciarTransacao();
			dao().gravar(pessoa);
			dao().commitTransacao();
		} catch (final Exception e) {
			dao().rollbackTransacao();
			throw new AplicacaoException("Erro na gravação", 0, e);
		}

		return "Usuário inativado com sucesso: " + pessoa.getSesbPessoa() + pessoa.getMatricula();
	}
	
	
	public CpToken gerarUrlPermanente(Long idRef) {
		try {
			CpToken sigaUrlPermanente = new CpToken();
			sigaUrlPermanente = dao().obterCpTokenPorTipoIdRef(1L,idRef); //Se tem token não expirado, devolve token
			if (sigaUrlPermanente == null ) {
				sigaUrlPermanente = new CpToken();
				//Seta tipo 1 - Token para URL Permamente
				sigaUrlPermanente.setIdTpToken(1L);
				
				sigaUrlPermanente.setToken(SigaUtil.randomAlfanumerico(128));
				sigaUrlPermanente.setIdRef(idRef);

				try {
					dao().gravar(sigaUrlPermanente);
				} catch (final Exception e) {
	
					throw new AplicacaoException("Erro na gravação", 0, e);
				}
			} 
			return sigaUrlPermanente;

			
		} catch (final Exception e) {
			throw new AplicacaoException("Ocorreu um erro ao gerar o Token.", 0, e);
		}
	}
		
	
	public String obterURLPermanente(String tipoLink, String token) {
		String urlPermanente = Prop.get("/sigaex.autenticidade.url").replace("/sigaex/public/app/autenticar", "");
		
		urlPermanente +=  "/siga/public/app/sigalink/"+tipoLink+"/"+token;
		
		return urlPermanente;
	}

	public void gravarMarcador(final Long id, final DpPessoa cadastrante, final DpLotacao lotacao, final CpIdentidade identidade, 
			final String descricao, final String descrDetalhada, final CpMarcadorCorEnum idCor, final CpMarcadorIconeEnum idIcone, final Integer grupoId, 
			final CpTipoMarcadorEnum idTpMarcador, final CpMarcadorTipoAplicacaoEnum idTpAplicacao, final CpMarcadorTipoDataEnum idTpDataPlanejada, 
			final CpMarcadorTipoDataEnum idTpDataLimite, 
			final CpMarcadorTipoExibicaoEnum idTpExibicao,	final CpMarcadorTipoTextoEnum idTpTexto, final CpMarcadorTipoInteressadoEnum idTpInteressado 
			) throws Exception {
		if (idTpMarcador == CpTipoMarcadorEnum.TIPO_MARCADOR_SISTEMA)
			throw new AplicacaoException ("Não é permitido o cadastro de marcadores de sistema.");
			
		if (descricao == null)
			throw new AplicacaoException ("Preencha a descrição do marcador.");
			
		if (descricao.length() > 40) 
			throw new AplicacaoException ("Descrição do marcador tem mais de 40 bytes.");
		
		String msgLotacao = SigaMessages.getMessage("usuario.lotacao");
		List<CpMarcador> listaMarcadoresLotacao = dao().listarCpMarcadoresPorLotacao(lotacao, true);
		List<CpMarcador> listaMarcadoresLotacaoEGerais = new ArrayList<CpMarcador> (listaMarcadoresLotacao);
		listaMarcadoresLotacaoEGerais.addAll(dao().listarCpMarcadoresGerais(true));
		
		if (idTpMarcador == CpTipoMarcadorEnum.TIPO_MARCADOR_LOTACAO && id == null 
				&& listaMarcadoresLotacao.size() > 9) 
			throw new AplicacaoException ("Atingiu o limite de 10 marcadores possíveis para " + msgLotacao);
		
		if (id == null && (listaMarcadoresLotacaoEGerais.stream()
				.filter(mar -> mar.getDescrMarcador()
						.equals(descricao)).count() > 0)) 
			throw new AplicacaoException ("Já existe um marcador Geral ou da " + msgLotacao 
					+ " com esta descrição: " + descricao);

		if (id != null) {
			CpMarcador marcadorAnt = new CpMarcador();
			CpMarcador marcador = new CpMarcador();

			marcadorAnt = dao().consultar(id, CpMarcador.class, false);
			if (marcadorAnt != null) {
				marcador.setHisIdIni(marcadorAnt.getHisIdIni());
				marcador.setOrdem(marcadorAnt.getOrdem());
				marcador.setDpLotacaoIni(marcadorAnt.getDpLotacaoIni());
				marcador.setDescrMarcador(descricao);
				marcador.setDescrDetalhada(descrDetalhada);
				marcador.setGrupoMarcador(2);
				marcador.setCpTipoMarcador(idTpMarcador);
				marcador.setIdCor(idCor);
				marcador.setIdIcone(idIcone);
				marcador.setIdTpAplicacao(idTpAplicacao);
				marcador.setIdTpDataPlanejada(idTpDataPlanejada);
				marcador.setIdTpDataLimite(idTpDataLimite);
				marcador.setIdTpExibicao(idTpExibicao);
				marcador.setIdTpTexto(idTpTexto);
				marcador.setIdTpInteressado(idTpInteressado);
				dao().gravarComHistorico(marcador, marcadorAnt, null, identidade);
			} else {
				throw new AplicacaoException ("Marcador não existente para esta " + msgLotacao 
						+ " (" + id.toString() + ").");
			}
		} else {
			Integer ordem = listaMarcadoresLotacao.size() + 1; 
			CpMarcador marcador = new CpMarcador();
			marcador.setDescrMarcador(descricao);
			marcador.setDescrDetalhada(descrDetalhada);
			marcador.setGrupoMarcador(grupoId);
			marcador.setCpTipoMarcador(idTpMarcador);
			marcador.setIdCor(idCor);
			marcador.setIdIcone(idIcone);
			marcador.setIdTpAplicacao(idTpAplicacao);
			marcador.setIdTpDataPlanejada(idTpDataPlanejada);
			marcador.setIdTpDataLimite(idTpDataLimite);
			marcador.setIdTpExibicao(idTpExibicao);
			marcador.setIdTpTexto(idTpTexto);
			marcador.setIdTpInteressado(idTpInteressado);
			marcador.setDpLotacaoIni(lotacao.getLotacaoInicial());
			marcador.setOrdem(ordem);
			
			dao().gravarComHistorico(marcador, null, null, identidade);
		}
	}
	
}